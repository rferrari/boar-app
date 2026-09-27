# BigMoeOnEdge patches

These patches to [BigMoeOnEdge](https://github.com/Helldez/BigMoeOnEdge) (commit `74ba18f`) and
its llama.cpp are from [AndroidLM](https://github.com/Phineas1500/AndroidLM) (`patches/`,
Apache License 2.0), unchanged. `scripts/build-native-engines.sh` applies them before building.

- `0001` one persistent, pinned thread pool per session
- `0002` a follow-up turn appends to the conversation instead of re-reading it
- `0003` engine priority (`BMOE_NICE`)
- `0004` thread placement on the fast cores (`BMOE_CPUMASK`), restored when the app is backgrounded
- `0005` progress while reading the prompt
- `0006` repacked dense weights (`BMOE_REPACK=1`)
- `llama.cpp/0001` ik_llama.cpp's ARM kernels for 2- and 3-bit experts (about 2x faster prompt reading)

AndroidLM measured these on a Pixel 8 Pro: `notes/2026-09-24-speed-levers.md` and
`notes/2026-09-25-iqk-port.md` in that repository.
