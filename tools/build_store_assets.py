#!/usr/bin/env python3
"""Build deterministic Google Play artwork from ImageGen and real UI captures."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "store-assets"
SOURCES = OUTPUT / "sources"
SCREENSHOTS = OUTPUT / "screenshots"
PREVIEW = OUTPUT / "preview"

FEATURE_SOURCE = SOURCES / "feature-background-imagegen.png"
PORTRAIT_SOURCE = SOURCES / "screenshot-background-imagegen.png"
MARK_SOURCE = ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_mark_v3.png"

FIRA_BOLD = ROOT / "app/src/main/res/font/fira_sans_condensed_bold.ttf"
FIRA_SEMIBOLD = ROOT / "app/src/main/res/font/fira_sans_condensed_semibold.ttf"
GOLOS = ROOT / "app/src/main/res/font/golos_text.ttf"

INK = "#06191B"
CYAN = "#57D9F4"
MINT = "#38D6BD"
WHITE = "#F5F7F3"
RED = "#E73A4C"
MUTED = "#AFC5C2"

SHOT_SPECS = [
    {
        "source": "docs/preview-matchday-typography-v259.png",
        "title": "МАТЧ-ДЕНЬ\nБЕЗ ШУМА",
        "subtitle": "Россия и СНГ: события, поиск и фильтры",
    },
    {
        "source": "docs/preview-analysis-typography-v259.png",
        "title": "НЕ ПРОГНОЗ.\nПРОВЕРКА",
        "subtitle": "Пять факторов и один следующий шаг",
    },
    {
        "source": "docs/preview-ui-evidence-v257.png",
        "title": "ВИДНО, ЧЕГО\nНЕ ХВАТАЕТ",
        "subtitle": "Источники, свежесть и главный пробел",
    },
    {
        "source": "docs/preview-verification-recipe-collapsed.png",
        "title": "ПРОТОКОЛ\nВМЕСТО ДОГАДОК",
        "subtitle": "Фиксируйте факты и сверяйте источники",
    },
    {
        "source": "docs/preview-ui-navigation-v257.png",
        "title": "СТАРТ БЕЗ\nЛИШНИХ ВОПРОСОВ",
        "subtitle": "Четыре шага и встроенное обучение",
    },
    {
        "source": "docs/preview-analysis-guide-v256.png",
        "title": "СТАТУСЫ\nОБЪЯСНЕНЫ",
        "subtitle": "Гайд помогает читать карту данных",
    },
]


def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), size=size)


def cover(image: Image.Image, width: int, height: int, align_y: float = 0.5) -> Image.Image:
    source_ratio = image.width / image.height
    target_ratio = width / height
    if source_ratio > target_ratio:
        resized_height = height
        resized_width = round(height * source_ratio)
    else:
        resized_width = width
        resized_height = round(width / source_ratio)
    resized = image.resize((resized_width, resized_height), Image.Resampling.LANCZOS)
    left = max(0, (resized_width - width) // 2)
    top = max(0, round((resized_height - height) * align_y))
    return resized.crop((left, top, left + width, top + height))


def rounded_image(image: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width, image.height), radius=radius, fill=255)
    result = image.convert("RGBA")
    result.putalpha(mask)
    return result


def add_shadow(canvas: Image.Image, box: tuple[int, int, int, int], radius: int = 28) -> None:
    left, top, right, bottom = box
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        (left - 18, top - 6, right + 18, bottom + 24),
        radius=32,
        fill=(0, 0, 0, 150),
    )
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))


def build_icon() -> Path:
    background = cover(Image.open(PORTRAIT_SOURCE).convert("RGB"), 512, 512, align_y=0.58)
    background = ImageEnhance.Brightness(background).enhance(0.68).convert("RGBA")
    tint = Image.new("RGBA", background.size, (0, 18, 20, 110))
    background = Image.alpha_composite(background, tint)

    draw = ImageDraw.Draw(background)
    draw.ellipse((35, 35, 477, 477), outline=(87, 217, 244, 38), width=3)
    draw.ellipse((68, 68, 444, 444), outline=(56, 214, 189, 30), width=2)

    mark = Image.open(MARK_SOURCE).convert("RGBA")
    bbox = mark.getbbox()
    if bbox:
        mark = mark.crop(bbox)
    mark.thumbnail((420, 420), Image.Resampling.LANCZOS)
    x = (512 - mark.width) // 2
    y = (512 - mark.height) // 2
    background.alpha_composite(mark, (x, y))

    destination = OUTPUT / "icon-512.png"
    background.save(destination, format="PNG", optimize=True)
    return destination


def build_feature() -> Path:
    source = Image.open(FEATURE_SOURCE).convert("RGB")
    canvas = cover(source, 1024, 500, align_y=0.48).convert("RGBA")

    veil = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    veil_draw = ImageDraw.Draw(veil)
    veil_draw.rectangle((230, 72, 794, 428), fill=(1, 21, 23, 105))
    veil = veil.filter(ImageFilter.GaussianBlur(56))
    canvas = Image.alpha_composite(canvas, veil)

    draw = ImageDraw.Draw(canvas)
    title_font = font(FIRA_BOLD, 83)
    subtitle_font = font(GOLOS, 24)
    label_font = font(FIRA_SEMIBOLD, 20)

    label = "СПОРТ • ФАКТЫ • КОНТРОЛЬ"
    title = "СПОРТ ПУЛЬС"
    subtitle = "Матч-день. Источники. Следующий шаг."

    label_width = draw.textbbox((0, 0), label, font=label_font)[2]
    title_width = draw.textbbox((0, 0), title, font=title_font)[2]
    subtitle_width = draw.textbbox((0, 0), subtitle, font=subtitle_font)[2]

    center_x = 512
    draw.rounded_rectangle((center_x - label_width // 2 - 18, 122, center_x + label_width // 2 + 18, 158), radius=18, fill=(4, 34, 35, 220), outline=(87, 217, 244, 120), width=1)
    draw.text((center_x - label_width // 2, 128), label, font=label_font, fill=MINT)
    draw.text((center_x - title_width // 2, 174), title, font=title_font, fill=WHITE)
    draw.text((center_x - subtitle_width // 2, 284), subtitle, font=subtitle_font, fill=WHITE)
    draw.line((center_x - 160, 341, center_x + 160, 341), fill=CYAN, width=3)
    draw.ellipse((center_x + 172, 335, center_x + 184, 347), fill=RED)

    destination = OUTPUT / "feature-graphic-1024x500.png"
    canvas.convert("RGB").save(destination, format="PNG", optimize=True)
    return destination


def build_screenshot(index: int, spec: dict[str, str]) -> Path:
    background = cover(Image.open(PORTRAIT_SOURCE).convert("RGB"), 1080, 1920, align_y=0.5)
    if index % 2 == 0:
        background = background.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    background = ImageEnhance.Brightness(background).enhance(0.76).convert("RGBA")
    background = Image.alpha_composite(background, Image.new("RGBA", background.size, (0, 12, 14, 42)))

    draw = ImageDraw.Draw(background)
    title_font = font(FIRA_BOLD, 78)
    subtitle_font = font(GOLOS, 27)
    index_font = font(FIRA_BOLD, 92)
    rail_font = font(FIRA_SEMIBOLD, 19)

    draw.text((70, 58), spec["title"], font=title_font, fill=WHITE, spacing=0)
    draw.text((72, 249), spec["subtitle"], font=subtitle_font, fill=MUTED)
    draw.line((72, 310, 950, 310), fill=(87, 217, 244, 110), width=2)
    draw.ellipse((944, 302, 960, 318), fill=RED)

    shot = Image.open(ROOT / spec["source"]).convert("RGB")
    shot = shot.resize((700, 1556), Image.Resampling.LANCZOS)
    shot = rounded_image(shot, radius=24)
    shot_box = (320, 364, 1020, 1920)
    add_shadow(background, shot_box)
    background.alpha_composite(shot, (shot_box[0], shot_box[1]))

    number = f"{index:02d}"
    draw.text((70, 490), number, font=index_font, fill=CYAN)
    draw.text((76, 604), "РЕАЛЬНЫЙ UI", font=rail_font, fill=WHITE)
    draw.line((84, 650, 84, 1690), fill=(87, 217, 244, 160), width=3)
    draw.ellipse((76, 1720, 92, 1736), fill=RED)

    destination = SCREENSHOTS / f"{index:02d}-{Path(spec['source']).stem}.png"
    background.convert("RGB").save(destination, format="PNG", optimize=True)
    return destination


def build_overview(icon_path: Path, feature_path: Path, screenshot_paths: list[Path]) -> Path:
    canvas = Image.new("RGB", (1920, 1080), INK).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    feature = Image.open(feature_path).convert("RGB").resize((1180, 576), Image.Resampling.LANCZOS)
    icon = Image.open(icon_path).convert("RGBA").resize((430, 430), Image.Resampling.LANCZOS)
    canvas.alpha_composite(feature.convert("RGBA"), (60, 42))
    canvas.alpha_composite(icon, (1415, 115))
    draw.text((1415, 566), "GOOGLE PLAY PACK", font=font(FIRA_SEMIBOLD, 28), fill=CYAN)

    thumb_width = 230
    thumb_height = 409
    gap = 26
    total_width = len(screenshot_paths) * thumb_width + (len(screenshot_paths) - 1) * gap
    start_x = (1920 - total_width) // 2
    for position, path in enumerate(screenshot_paths):
        thumb = Image.open(path).convert("RGB").resize((thumb_width, thumb_height), Image.Resampling.LANCZOS)
        canvas.alpha_composite(rounded_image(thumb, 12), (start_x + position * (thumb_width + gap), 646))

    destination = PREVIEW / "store-pack-overview.png"
    canvas.convert("RGB").save(destination, format="PNG", optimize=True)
    return destination


def write_manifest(paths: list[Path]) -> None:
    payload = {
        "product": "Спорт Пульс",
        "locale": "ru-RU",
        "generated_visual_sources": [
            str(FEATURE_SOURCE.relative_to(ROOT)),
            str(PORTRAIT_SOURCE.relative_to(ROOT)),
            str(MARK_SOURCE.relative_to(ROOT)),
        ],
        "exports": [
            {
                "path": str(path.relative_to(ROOT)),
                "width": Image.open(path).width,
                "height": Image.open(path).height,
                "mode": Image.open(path).mode,
                "bytes": path.stat().st_size,
            }
            for path in paths
        ],
    }
    (OUTPUT / "manifest.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    for directory in (OUTPUT, SOURCES, SCREENSHOTS, PREVIEW):
        directory.mkdir(parents=True, exist_ok=True)

    for required in (FEATURE_SOURCE, PORTRAIT_SOURCE, MARK_SOURCE, FIRA_BOLD, FIRA_SEMIBOLD, GOLOS):
        if not required.exists():
            raise FileNotFoundError(required)

    icon_path = build_icon()
    feature_path = build_feature()
    screenshot_paths = [build_screenshot(index, spec) for index, spec in enumerate(SHOT_SPECS, start=1)]
    overview_path = build_overview(icon_path, feature_path, screenshot_paths)
    write_manifest([icon_path, feature_path, *screenshot_paths, overview_path])


if __name__ == "__main__":
    main()
