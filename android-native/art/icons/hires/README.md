Higher-resolution art, one file per boar id (`<id>.png` or `<id>.webp`, square, no frame). When a file is here,
`scripts/build-truffle-icons.py` uses it instead of the crop from the source grid.

The current files are the grid crops upscaled 4x with Real-ESRGAN (realesrgan-x4plus-anime, ncnn-vulkan
v0.2.5.0), then saved at 512 px. The designer's originals replace them.
