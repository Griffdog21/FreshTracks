"""Synthesises every Fresh Tracks sound effect (no samples, no downloads) and writes OGG Vorbis files.

Loops are built in the frequency domain, so they repeat seamlessly.
Run: uv run --with numpy --with soundfile python tools/sound/synth.py
"""
from __future__ import annotations

import os

import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "freshtracks", "sounds")
rng = np.random.default_rng(20261009)


# ----------------------------------------------------------------------------------------- building blocks

def shaped_noise(seconds: float, spectrum) -> np.ndarray:
    """Periodic noise whose magnitude spectrum is spectrum(freqs). Loops seamlessly."""
    n = int(SR * seconds)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    mag = spectrum(freqs)
    phase = rng.uniform(0, 2 * np.pi, len(freqs))
    sig = np.fft.irfft(mag * np.exp(1j * phase), n)
    return sig / (np.max(np.abs(sig)) + 1e-9)


def band(lo: float, hi: float, slope: float = 2.0):
    def f(freqs):
        f0 = np.sqrt(lo * hi)
        width = np.log2(hi / lo) / 2
        x = np.log2(np.maximum(freqs, 1) / f0) / max(width, 1e-3)
        return np.exp(-np.abs(x) ** slope)
    return f


def mix_spectra(*parts):
    def f(freqs):
        return sum(w * p(freqs) for w, p in parts)
    return f


def periodic_lfo(n: int, cycles: int, depth: float, harmonics=(1.0,), seed=0) -> np.ndarray:
    t = np.arange(n) / n
    r = np.random.default_rng(seed)
    lfo = np.zeros(n)
    for i, amp in enumerate(harmonics):
        lfo += amp * np.sin(2 * np.pi * cycles * (i + 1) * t + r.uniform(0, 6.28))
    lfo /= max(1e-9, np.max(np.abs(lfo)))
    return 1.0 - depth * 0.5 + depth * 0.5 * lfo


def grains(n: int, rate: float, length_ms: float, seed: int, periodic=True) -> np.ndarray:
    """Sparse little crunches (granular snow texture)."""
    r = np.random.default_rng(seed)
    env = np.zeros(n)
    count = int(n / SR * rate)
    glen = int(SR * length_ms / 1000)
    shape = np.exp(-np.linspace(0, 6, glen))
    for _ in range(count):
        start = r.integers(0, n)
        amp = r.uniform(0.3, 1.0)
        idx = (start + np.arange(glen)) % n if periodic else start + np.arange(glen)
        idx = idx[idx < n]
        env[idx] += amp * shape[: len(idx)]
    return env


def envelope(n: int, attack: float, decay: float) -> np.ndarray:
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-np.maximum(0, t - attack) / max(decay, 1e-4))


def tone(freq: float, seconds: float, decay: float, harmonics=((1, 1.0),), detune=0.0) -> np.ndarray:
    n = int(SR * seconds)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for h, amp in harmonics:
        s += amp * np.sin(2 * np.pi * freq * h * (1 + detune * h) * t)
    return s * np.exp(-t / decay)


def lowpass(sig: np.ndarray, cutoff: float) -> np.ndarray:
    spec = np.fft.rfft(sig)
    freqs = np.fft.rfftfreq(len(sig), 1 / SR)
    spec *= 1 / (1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spec, len(sig))


def highpass(sig: np.ndarray, cutoff: float) -> np.ndarray:
    spec = np.fft.rfft(sig)
    freqs = np.fft.rfftfreq(len(sig), 1 / SR)
    spec *= 1 - 1 / (1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spec, len(sig))


def normalize(sig: np.ndarray, peak: float = 0.9) -> np.ndarray:
    return sig * (peak / (np.max(np.abs(sig)) + 1e-9))


def write(name: str, sig: np.ndarray, peak: float = 0.9) -> None:
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, normalize(sig, peak).astype(np.float32), SR, format="OGG", subtype="VORBIS")
    print(f"{name:28s} {len(sig) / SR:5.2f}s")


def one_shot_fade(sig: np.ndarray, fade_ms: float = 15) -> np.ndarray:
    n = int(SR * fade_ms / 1000)
    sig = sig.copy()
    sig[-n:] *= np.linspace(1, 0, n)
    return sig


# ----------------------------------------------------------------------------------------- loops

def carve():
    secs = 3.0
    n = int(SR * secs)
    hiss = shaped_noise(secs, mix_spectra((1.0, band(1800, 7500, 2.2)), (0.35, band(500, 1500))))
    crunch = shaped_noise(secs, band(900, 4000)) * grains(n, 180, 9, 11)
    mod = periodic_lfo(n, 3, 0.35, (1.0, 0.4, 0.2), seed=1)
    return (hiss * 0.8 + crunch * 0.6) * mod


def glide():
    secs = 3.0
    n = int(SR * secs)
    return shaped_noise(secs, band(700, 3500, 1.6)) * periodic_lfo(n, 2, 0.25, (1.0, 0.3), seed=2)


def powder():
    secs = 4.0
    n = int(SR * secs)
    whoosh = shaped_noise(secs, mix_spectra((1.0, band(180, 900, 1.5)), (0.25, band(900, 2500))))
    puffs = shaped_noise(secs, band(300, 1200)) * grains(n, 40, 60, 21)
    return (whoosh + puffs * 0.5) * periodic_lfo(n, 4, 0.5, (1.0, 0.5, 0.3), seed=3)


def skid():
    secs = 2.0
    n = int(SR * secs)
    harsh = shaped_noise(secs, mix_spectra((1.0, band(1200, 9000, 1.4)), (0.5, band(300, 1200))))
    chatter = grains(n, 420, 6, 31)
    return harsh * (0.55 + 0.6 * chatter / (np.max(chatter) + 1e-9)) * periodic_lfo(n, 6, 0.3, seed=4)


def ice_scrape():
    secs = 2.0
    n = int(SR * secs)
    base = shaped_noise(secs, mix_spectra((1.0, band(2400, 2700, 4)), (0.8, band(3900, 4300, 4)), (0.6, band(1500, 8000))))
    return base * periodic_lfo(n, 8, 0.6, (1.0, 0.5), seed=5)


def rock_scrape():
    secs = 2.0
    n = int(SR * secs)
    gravel = shaped_noise(secs, band(200, 2500, 1.3)) * (0.3 + grains(n, 600, 4, 41))
    return gravel * periodic_lfo(n, 5, 0.4, seed=6)


def wind():
    secs = 6.0
    n = int(SR * secs)
    rush = shaped_noise(secs, mix_spectra((1.0, band(120, 900, 1.2)), (0.25, band(900, 3000))))
    whistle = shaped_noise(secs, mix_spectra((0.5, band(610, 650, 6)), (0.3, band(930, 980, 6))))
    return (rush + whistle * 0.35) * periodic_lfo(n, 3, 0.55, (1.0, 0.5, 0.25), seed=7)


def lift_hum():
    secs = 4.0
    n = int(SR * secs)
    t = np.arange(n) / SR
    motor = 0.6 * np.sin(2 * np.pi * 50 * t) + 0.3 * np.sin(2 * np.pi * 100 * t) + 0.15 * np.sin(2 * np.pi * 150 * t)
    rumble = shaped_noise(secs, band(60, 400, 1.5)) * 0.5
    creak = shaped_noise(secs, band(400, 1400)) * grains(n, 3, 120, 51) * 0.4
    return (motor * 0.5 + rumble + creak) * periodic_lfo(n, 2, 0.15, seed=8)


def blizzard():
    secs = 8.0
    n = int(SR * secs)
    roar = shaped_noise(secs, mix_spectra((1.0, band(80, 700, 1.2)), (0.4, band(700, 2500)), (0.2, band(2500, 7000))))
    howl = shaped_noise(secs, mix_spectra((0.6, band(420, 450, 7)), (0.4, band(700, 740, 7))))
    hiss = shaped_noise(secs, band(4000, 12000)) * grains(n, 900, 3, 61) * 0.3
    return (roar + howl * 0.5 + hiss) * periodic_lfo(n, 2, 0.6, (1.0, 0.6, 0.3), seed=9)


# ----------------------------------------------------------------------------------------- one-shots

def thump(freq=70, secs=0.4, decay=0.09):
    n = int(SR * secs)
    t = np.arange(n) / SR
    f = freq * (1 + 1.5 * np.exp(-t / 0.03))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)


def crunch(secs=0.35, lo=600, hi=5000, decay=0.08, seed=0):
    n = int(SR * secs)
    r = np.random.default_rng(seed)
    sig = r.normal(size=n)
    spec = np.fft.rfft(sig) * band(lo, hi)(np.fft.rfftfreq(n, 1 / SR))
    sig = np.fft.irfft(spec, n)
    return sig / (np.max(np.abs(sig)) + 1e-9) * envelope(n, 0.004, decay)


def pad(sig: np.ndarray, secs: float) -> np.ndarray:
    n = int(SR * secs)
    out = np.zeros(max(n, len(sig)))
    out[: len(sig)] += sig
    return out


def at(base: np.ndarray, sig: np.ndarray, offset_s: float, gain=1.0) -> np.ndarray:
    o = int(offset_s * SR)
    end = min(len(base), o + len(sig))
    base[o:end] += sig[: end - o] * gain
    return base


def click(seed: int, pitch: float = 1.0):
    snap = crunch(0.05, 2500 * pitch, 9000, 0.008, seed)
    ring = tone(1900 * pitch, 0.25, 0.05, ((1, 1.0), (2.76, 0.4), (5.4, 0.2)))
    return pad(snap, 0.25) + ring * 0.25


def click_in(i):
    s = np.zeros(int(SR * 0.6))
    at(s, click(10 + i, 1.0 + i * 0.03), 0.02)
    at(s, click(20 + i, 0.85 + i * 0.03), 0.17)
    at(s, thump(110, 0.2, 0.04), 0.17, 0.5)
    return s


def click_out(i):
    s = np.zeros(int(SR * 0.45))
    at(s, click(30 + i, 1.15 + i * 0.03), 0.02, 0.8)
    at(s, crunch(0.2, 800, 4000, 0.05, 40 + i), 0.08, 0.5)
    return s


def pop(i):
    s = np.zeros(int(SR * 0.5))
    at(s, thump(90 + i * 6, 0.3, 0.05), 0.0, 0.8)
    at(s, crunch(0.3, 900, 6000, 0.06, 50 + i), 0.0, 0.7)
    return s


def land(i):
    s = np.zeros(int(SR * 0.7))
    at(s, thump(60 + i * 5, 0.5, 0.12), 0.0, 1.0)
    at(s, crunch(0.5, 500, 5000, 0.12, 60 + i), 0.0, 0.9)
    at(s, crunch(0.4, 1500, 8000, 0.05, 70 + i), 0.03, 0.4)
    return s


def land_powder(i):
    s = np.zeros(int(SR * 0.9))
    poof = crunch(0.8, 120, 1400, 0.2, 80 + i)
    at(s, lowpass(poof, 1200), 0.0, 1.0)
    at(s, thump(50, 0.5, 0.15), 0.0, 0.5)
    return s


def wipeout(i):
    s = np.zeros(int(SR * 1.6))
    r = np.random.default_rng(90 + i)
    tms = 0.0
    for k in range(5):
        at(s, thump(55 + r.uniform(-10, 25), 0.4, 0.08), tms, 0.9 - k * 0.12)
        at(s, crunch(0.35, 400, 5000, 0.08, 100 + i * 10 + k), tms, 0.8 - k * 0.1)
        tms += r.uniform(0.14, 0.3)
    at(s, crunch(1.0, 200, 1500, 0.35, 99), 0.0, 0.35)
    return s


def core_shot():
    s = np.zeros(int(SR * 0.9))
    at(s, crunch(0.6, 1500, 9000, 0.15, 111), 0.0, 1.0)
    at(s, tone(700, 0.8, 0.2, ((1, 1.0), (2.4, 0.6), (4.1, 0.3))), 0.05, 0.5)
    return s


def bonk(i):
    s = np.zeros(int(SR * 0.4))
    at(s, thump(150 + i * 20, 0.3, 0.05), 0.0, 1.0)
    at(s, tone(320 + i * 30, 0.3, 0.06, ((1, 1.0), (2.1, 0.5))), 0.0, 0.4)
    return s


def trick():
    """Bright little chime: a rising pentatonic arpeggio with a shimmer."""
    s = np.zeros(int(SR * 1.2))
    notes = [659.25, 783.99, 987.77, 1318.51]
    for k, f in enumerate(notes):
        at(s, tone(f, 0.9, 0.25, ((1, 1.0), (2, 0.25), (3, 0.1))), k * 0.06, 0.6)
    at(s, crunch(0.5, 6000, 14000, 0.15, 120), 0.18, 0.15)
    return s


def chair_clack(i):
    s = np.zeros(int(SR * 0.7))
    for k in range(2):
        at(s, thump(140 + i * 10, 0.2, 0.03), k * 0.11, 0.7)
        at(s, tone(560 + i * 25, 0.3, 0.07, ((1, 1.0), (2.7, 0.5))), k * 0.11, 0.35)
    return s


def lift_bell():
    return tone(1046.5, 1.5, 0.45, ((1, 1.0), (2.0, 0.4), (3.0, 0.2), (4.2, 0.1))) + tone(1568, 1.5, 0.35) * 0.4


def main():
    loops = {
        "rig/carve": carve, "rig/glide": glide, "rig/powder": powder, "rig/skid": skid,
        "rig/ice_scrape": ice_scrape, "rig/rock_scrape": rock_scrape, "rig/wind": wind,
        "lift/hum": lift_hum, "weather/blizzard": blizzard,
    }
    for name, fn in loops.items():
        write(name, fn(), 0.8)
    for i in range(3):
        write(f"rig/click_in{i + 1}", one_shot_fade(click_in(i)))
        write(f"rig/click_out{i + 1}", one_shot_fade(click_out(i)))
        write(f"rig/pop{i + 1}", one_shot_fade(pop(i)))
        write(f"rig/land{i + 1}", one_shot_fade(land(i)))
        write(f"rig/land_powder{i + 1}", one_shot_fade(land_powder(i)))
        write(f"rig/wipeout{i + 1}", one_shot_fade(wipeout(i)))
        write(f"rig/bonk{i + 1}", one_shot_fade(bonk(i)))
        write(f"lift/chair_clack{i + 1}", one_shot_fade(chair_clack(i)))
    write("rig/core_shot", one_shot_fade(core_shot()))
    write("rig/trick", one_shot_fade(trick()), 0.7)
    write("lift/bell", one_shot_fade(lift_bell()), 0.6)


if __name__ == "__main__":
    main()
