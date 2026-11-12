"""Build an editable .pptx version of the MasteryPath design deck.

Run:  python docs/design/deck/build_pptx.py
Out:  docs/design/deck/MasteryPath-设计.pptx
"""

from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"
OUT = HERE / "MasteryPath-设计.pptx"

NAVY = RGBColor(0x1B, 0x3F, 0x73)
BLUE = RGBColor(0x25, 0x63, 0xEB)
AMBER = RGBColor(0xC8, 0x76, 0x1E)
DEEP_AMBER = RGBColor(0xA8, 0x62, 0x1A)
PALE_BLUE = RGBColor(0xEE, 0xF4, 0xFD)
PALE_AMBER = RGBColor(0xFD, 0xF3, 0xE7)
GRAY = RGBColor(0x8A, 0x94, 0xA6)
INK = RGBColor(0x1F, 0x29, 0x37)
SOFT_INK = RGBColor(0x5B, 0x6A, 0x80)
LINE = RGBColor(0xE7, 0xEE, 0xF9)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
HEADER_DARK = RGBColor(0x16, 0x23, 0x3A)
HEADER_AMBER = RGBColor(0x4A, 0x35, 0x20)

FONT = "微软雅黑"


def set_font(run, size, bold=False, color=INK, font=FONT):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = font
    rpr = run._r.get_or_add_rPr()
    ea = rpr.find("{http://schemas.openxmlformats.org/drawingml/2006/main}ea")
    if ea is None:
        from lxml import etree

        ea = etree.SubElement(rpr, "{http://schemas.openxmlformats.org/drawingml/2006/main}ea")
    ea.set("typeface", font)


def add_rich(tf, text, size, color=INK, bold=False, align=PP_ALIGN.LEFT,
             line_spacing=1.15, space_after=2):
    """Parse **bold** segments into runs."""
    tf.word_wrap = True
    tf.vertical_anchor = MSO_ANCHOR.TOP
    first = True
    for raw_line in text.split("\n"):
        p = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        p.alignment = align
        p.line_spacing = line_spacing
        p.space_after = Pt(space_after)
        parts = raw_line.split("**")
        for idx, part in enumerate(parts):
            if part == "":
                continue
            run = p.add_run()
            run.text = part
            set_font(run, size, bold or idx % 2 == 1, color)


def textbox(slide, x, y, w, h, text, size, color=INK, bold=False,
            align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, spacing=1.15):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    tf.vertical_anchor = anchor
    add_rich(tf, text, size, color, bold, align, spacing)
    return box


def rect(slide, x, y, w, h, fill, line=None, radius=0.06, line_w=0.75, dash=None):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    shape.adjustments[0] = radius
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    if line is None:
        shape.line.fill.background()
    else:
        shape.line.color.rgb = line
        shape.line.width = Pt(line_w)
        if dash:
            from pptx.enum.dml import MSO_LINE_DASH_STYLE

            shape.line.dash_style = MSO_LINE_DASH_STYLE.DASH
    shape.shadow.inherit = False
    shape.text_frame.text = ""
    return shape


def boxed_text(slide, x, y, w, h, text, size, fill=PALE_BLUE, color=SOFT_INK,
               line=None, bold=False, pad=0.08, align=PP_ALIGN.LEFT):
    rect(slide, x, y, w, h, fill, line)
    tb = textbox(slide, x + pad, y + pad * 0.7, w - pad * 2, h - pad * 1.4, text, size, color, bold, align)
    return tb


def picture_fit(slide, path, x, y, w, h, bg=RGBColor(0xF7, 0xF8, 0xFA)):
    from PIL import Image

    rect(slide, x, y, w, h, bg, LINE)
    with Image.open(path) as im:
        iw, ih = im.size
    scale = min(w / iw, h / ih)
    pw, ph = iw * scale, ih * scale
    slide.shapes.add_picture(str(path), Inches(x + (w - pw) / 2), Inches(y + (h - ph) / 2),
                             Inches(pw), Inches(ph))


def picture_cover(slide, path, x, y, w, h):
    from PIL import Image

    with Image.open(path) as im:
        iw, ih = im.size
    pic = slide.shapes.add_picture(str(path), Inches(x), Inches(y), Inches(w), Inches(h))
    ar_img, ar_box = iw / ih, w / h
    if ar_img > ar_box:
        keep = ar_box / ar_img
        c = (1 - keep) / 2
        pic.crop_left = c
        pic.crop_right = c
    else:
        keep = ar_img / ar_box
        c = (1 - keep) / 2
        pic.crop_top = c
        pic.crop_bottom = c


def arrow(slide, x1, y1, x2, y2, color=BLUE, dash=False, width=1.2):
    from lxml import etree
    from pptx.enum.shapes import MSO_CONNECTOR
    from pptx.enum.dml import MSO_LINE_DASH_STYLE
    from pptx.oxml.ns import qn

    conn = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    conn.line.color.rgb = color
    conn.line.width = Pt(width)
    if dash:
        conn.line.dash_style = MSO_LINE_DASH_STYLE.DASH
    ln = conn.line._get_or_add_ln()
    tail = etree.SubElement(ln, qn("a:tailEnd"))
    tail.set("type", "triangle")
    tail.set("w", "med")
    tail.set("len", "med")
    return conn


def slide_base(prs, section, title, subtitle, page, total=12):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    bg = s.background.fill
    bg.solid()
    bg.fore_color.rgb = WHITE
    textbox(s, 0.55, 0.40, 2.05, 0.32, section, 15, BLUE, True)
    textbox(s, 2.62, 0.30, 10.15, 0.55, title, 24, NAVY, True)
    if subtitle:
        textbox(s, 0.55, 1.00, 12.2, 0.34, subtitle, 10.5, GRAY)
    textbox(s, 0.55, 7.12, 6.0, 0.22, "MasteryPath · 系统设计", 8, RGBColor(0xA7, 0xB0, 0xC0))
    textbox(s, 11.55, 7.12, 1.2, 0.22, f"{page:02d} / {total}", 8, RGBColor(0xA7, 0xB0, 0xC0), align=PP_ALIGN.RIGHT)
    return s


def table(slide, x, y, w, rows, col_w, header, row_h=0.62, header_h=0.34,
          font_size=9.0, header_size=9.0):
    n_rows, n_cols = len(rows) + 1, len(col_w)
    shape = slide.shapes.add_table(n_rows, n_cols, Inches(x), Inches(y), Inches(w), Inches(header_h + row_h * len(rows)))
    tbl = shape.table
    tbl.first_row = False
    tbl.horz_banding = False
    for i, cw in enumerate(col_w):
        tbl.columns[i].width = Inches(cw)
    tbl.rows[0].height = Inches(header_h)
    for r in range(1, n_rows):
        tbl.rows[r].height = Inches(row_h)

    for c, text in enumerate(header):
        cell = tbl.cell(0, c)
        cell.fill.solid()
        cell.fill.fore_color.rgb = HEADER_DARK if c < n_cols - 1 else HEADER_AMBER
        cell.margin_left = Inches(0.08)
        cell.margin_right = Inches(0.06)
        cell.margin_top = Inches(0.04)
        cell.margin_bottom = Inches(0.04)
        tf = cell.text_frame
        tf.word_wrap = True
        p = tf.paragraphs[0]
        p.alignment = PP_ALIGN.LEFT
        run = p.add_run()
        run.text = text
        set_font(run, header_size, True, WHITE)

    for r, row in enumerate(rows, start=1):
        for c, text in enumerate(row):
            cell = tbl.cell(r, c)
            cell.fill.solid()
            if c == n_cols - 1:
                cell.fill.fore_color.rgb = PALE_AMBER
            else:
                cell.fill.fore_color.rgb = WHITE if r % 2 == 1 else PALE_BLUE
            cell.margin_left = Inches(0.08)
            cell.margin_right = Inches(0.06)
            cell.margin_top = Inches(0.05)
            cell.margin_bottom = Inches(0.05)
            tf = cell.text_frame
            tf.word_wrap = True
            tf.vertical_anchor = MSO_ANCHOR.TOP
            add_rich(tf, text, font_size,
                     DEEP_AMBER if c == n_cols - 1 else SOFT_INK,
                     False, PP_ALIGN.LEFT, 1.12, 1)
    return tbl


def build():
    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    # ---------------- 1 cover ----------------
    s = prs.slides.add_slide(prs.slide_layouts[6])
    bg = s.background.fill
    bg.solid()
    bg.fore_color.rgb = WHITE
    textbox(s, 0.9, 1.15, 10.0, 0.34, "系统分析与设计 · 第二部分", 14, BLUE, True)
    textbox(s, 0.9, 1.55, 11.0, 1.9, "MasteryPath\n精通之路", 46, NAVY, True, spacing=1.0)
    textbox(s, 0.9, 3.42, 11.0, 0.32, "SYSTEM DESIGN OF AN AGENTIC MASTERY LEARNING SYSTEM", 10, GRAY)
    rect(s, 0.9, 3.95, 0.95, 0.05, BLUE, radius=0)
    textbox(s, 0.9, 4.30, 11.0, 1.3,
            "以知识地图为中心的掌握式学习智能体\n**教学交给模型，推进交给引擎**\n王君逸 · 程攀 · 卫梓豪 · 王沛璁",
            13, RGBColor(0x4B, 0x55, 0x63), spacing=1.5)
    textbox(s, 0.9, 7.05, 6.0, 0.22, "MasteryPath · 系统设计", 8, RGBColor(0xA7, 0xB0, 0xC0))
    textbox(s, 10.6, 7.05, 2.0, 0.22, "2026-11 · Week 10", 8, RGBColor(0xA7, 0xB0, 0xC0), align=PP_ALIGN.RIGHT)

    # ---------------- 2 pain ----------------
    s = slide_base(prs, "01 需求与痛点", "资料并不缺，缺的是“学到哪、算不算会”",
                   "自学卡住的地方，不在信息获取，而在路径、判据与遗忘。", 2)
    left = [
        ("「资料收藏了一堆，不知道从哪开始。」", "目标模糊，路径靠感觉；收藏不等于路线。"),
        ("「看完了感觉懂了，一合上书就忘。」", "流利错觉：重复阅读制造“会了”的体感，却不带来保持。"),
        ("「做了题，错了也不知道该不该往下学。」", "没有判据，推进只能靠自我感觉。"),
    ]
    y = 1.45
    textbox(s, 0.55, y, 5.6, 0.3, "学习者在什么时候卡住", 13, NAVY, True)
    y += 0.40
    for q, n in left:
        textbox(s, 0.55, y, 5.7, 0.30, q, 11.5, BLUE, True)
        textbox(s, 0.55, y + 0.30, 5.7, 0.34, n, 9.5, GRAY)
        y += 0.88

    textbox(s, 6.55, 1.45, 5.9, 0.3, "现有工具能帮到哪一步", 13, NAVY, True)
    cards = [
        ("笔记 / 资料库", "能存资料、能双链——但不排路径，也不判断是否掌握。", False),
        ("刷题 App", "能出题、能对答案——题海驱动，不排复习、不看类型。", False),
        ("Anki / SuperMemo", "能排间隔复习——卡片要自己造，没有地图与讲解。", False),
        ("没有一个工具把“地图—学习—检验—复习”连成闭环", "而“是否掌握”这件事，目前几乎全靠用户自己说。", True),
    ]
    y = 1.85
    for title, body, amber in cards:
        fill = PALE_AMBER if amber else PALE_BLUE
        boxed_text(s, 6.55, y, 6.2, 1.02,
                   f"**{title}**\n{body}", 9.5, fill,
                   DEEP_AMBER if amber else SOFT_INK,
                   AMBER if amber else None, pad=0.12)
        y += 1.12

    # ---------------- 3 product ----------------
    s = slide_base(prs, "01 定位", "一条目标变成可编辑地图，围绕它跑闭环",
                   "地图是唯一真相源；题目、证据、复习都围绕它发生。", 3)
    items = [
        ("一张地图 **模块 + 知识点**", "记忆 / 程序 / 概念 / 设计——**类型决定“什么叫会”**，创建后只读。"),
        ("三种模式 **大纲 / 学习 / 复习**", "能力由后端**调用期门控**，不只禁用按钮；工具不归类，服务直接起不来。"),
        ("一个判据 **0.9 或定性通过**", "判分、门槛、调度全部零模型调用——模型可以教，**不能放行**。"),
    ]
    y = 1.45
    textbox(s, 0.55, y, 5.7, 0.3, "MasteryPath 帮学习者完成什么", 13, NAVY, True)
    y += 0.42
    for t, b in items:
        textbox(s, 0.55, y, 5.9, 0.28, t, 11, NAVY, True)
        textbox(s, 0.55, y + 0.28, 5.9, 0.36, b, 9.5, SOFT_INK)
        y += 0.86
    boxed_text(s, 0.55, 4.45, 5.9, 1.25,
               "**相比第一部分，需求变了三处**\n新增练习卷（**不计入掌握**）· 题卡事件权威化 · 知识库与主题多对多。\n原因：单题回合无法支撑考前自测，而刷卷不能变成刷分。",
               9.5, PALE_AMBER, RGBColor(0x8A, 0x6A, 0x3F), RGBColor(0xEB, 0xD3, 0xB4), pad=0.12)

    textbox(s, 6.75, 1.45, 5.9, 0.3, "两条动线，共用题库与知识库", 13, NAVY, True)
    lanes = [
        ("掌握之路（写掌握分）", [("地图", "4 类知识点"), ("讲 / 考", "单槽位出题"), ("判分", "0.9 或定性"), ("复习", "SM-2 排期")], False),
        ("刷题（不写掌握分）", [("题库", "判分后入库"), ("筛选", "标签 / 知识库"), ("刷题", "10 题 · 即时判"), ("练习卷", "1–10 题")], True),
    ]
    y = 1.92
    for name, chips, amber in lanes:
        textbox(s, 6.75, y, 5.8, 0.26, name, 10.5, NAVY, True)
        y += 0.32
        cx = 6.75
        for i, (a, b) in enumerate(chips):
            fill = PALE_AMBER if amber else PALE_BLUE
            boxed_text(s, cx, y, 1.20, 0.62, f"**{a}**\n{b}", 7.5, fill,
                       DEEP_AMBER if amber else RGBColor(0x4A, 0x6B, 0x9A), None, pad=0.06)
            cx += 1.20
            if i < 3:
                textbox(s, cx, y + 0.14, 0.22, 0.3, "›", 13, RGBColor(0xB9, 0xCB, 0xE4), True, PP_ALIGN.CENTER)
                cx += 0.24
        y += 0.95
    boxed_text(s, 6.75, 4.45, 5.8, 0.95,
               "**分界：**只有掌握之路写掌握分与证据；刷题与练习卷只回写该题作答，不进复习队列。",
               9.5, RGBColor(0xF3, 0xF6, 0xFB), SOFT_INK, pad=0.12)

    # ---------------- 4 screenshots ----------------
    s = slide_base(prs, "01 界面", "四个界面，两条动线",
                   "图集与仪表盘只读投影；工作区才产生写事务；刷题只回写该题作答。", 4)
    shots = [
        ("s1-atlas.png", "图集：我有哪些主题、几个到期"),
        ("s2-dashboard.png", "仪表盘：这个主题到哪了"),
        ("s3-card.png", "题卡：一次一道，作答是下一条消息"),
        ("s5-practice.png", "刷题：从题库抽题，即时判分"),
    ]
    pos = [(0.55, 1.42), (3.62, 1.42), (6.69, 1.42), (9.76, 1.42)]
    for (x, y), (f, cap) in zip(pos, shots):
        picture_fit(s, ASSETS / f, x, y, 2.99, 2.70)
        textbox(s, x, y + 2.74, 2.99, 0.36, cap, 8.2, GRAY)
    boxed_text(s, 0.55, 5.55, 12.2, 0.55,
               "**读侧无模型：**图集与仪表盘只读聚合投影；**写侧有门控：**只有工作区会产生写事务。",
               9.5, RGBColor(0xF3, 0xF6, 0xFB), SOFT_INK, pad=0.12)

    # ---------------- 5 tradeoffs ----------------
    s = slide_base(prs, "02 三个关键设计权衡", "三条决策决定系统“不会坏在哪”",
                   "每条都对应一种具体的失败模式；写清代价，而不是只写选择。", 5)
    rows = [
        ("权衡 1\n判定权归属",
         "模型输出的两次自评不一致，无法回归；若门槛由模型判，“掌握”就不可复核。",
         "**判定下沉到 domain 纯函数**：判分 / 门槛 / 门控 / 调度零模型调用。\n代价：规则改动必须走代码 + ADR；定性评估仍是唯一的“半确定性”边界。"),
        ("权衡 2\n状态一致性",
         "一次出题要同时写待答题、题目事务、事件；一次判分要同时写作答、证据、掌握度、复习队列——分表多写必留脏状态。",
         "**单聚合 + version CAS + 事件同 revision**；一次工具调用 = 一次写事务。\n代价：聚合随路径变大有写放大；读-改-写期间无法跨聚合事务。"),
        ("权衡 3\n能力边界",
         "23 个工具 × 3 种模式 = 69 种组合，UI 只能表达 3 个模式按钮；越权一旦发生，事后无法追溯意图。",
         "**静态分类表 + 调用期门控 + fail-closed**：未归类工具启动即失败；条件挂载让模型看不到无效工具。\n代价：分类表与 schema 需双写；越权拒绝会消耗一次预算。"),
    ]
    table(s, 0.55, 1.45, 12.2, rows, [2.3, 4.2, 5.7],
          ["权衡维度", "约束（为什么会坏）", "我们的选择与代价"], row_h=1.06, header_h=0.36, font_size=9.2)
    textbox(s, 0.55, 6.55, 12.2, 0.3,
            "共同点：三条都把“不可靠的判断”换成“可断言的结构”。", 9.5, GRAY)

    # ---------------- 6 agent architecture ----------------
    s = slide_base(prs, "02 智能体架构", "提示只读快照，事实只由一次写事务产生",
                   "读侧装配上下文，写侧只有一条路；工具与循环都没有写权限。", 6)
    layers = [
        ("传输\nWS / REST", "**WsTurnHandler** 只做解析与转发：一次连接一条请求，事件逐帧推送"),
        ("回合装配\nTurnRunner", "**读侧全部在这里**：状态快照 + 最近 6 张卡复盘 + ≤20 条历史 + 8 块提示 + 工具表"),
        ("循环\nAgentLoop", "≤6 轮 · ≤6 次工具 · 出题即收尾；只产事件，不写库"),
        ("工具层\n23 个", "schema 校验 → 模式门控 → 预算 → 调 service；不直接改状态"),
        ("服务\nLearningService", "**唯一写入口**：一次工具调用 = 一次 mutate；校验、派生、发事件都在这层"),
        ("存储\nLearningStore", "读版本 → 改聚合 → 版本比对写回；冲突重试 ≤3，事件同 revision 入账"),
    ]
    y = 1.42
    for i, (name, desc) in enumerate(layers):
        textbox(s, 0.55, y + 0.06, 1.25, 0.42, name, 8.5, NAVY, True, spacing=1.0)
        boxed_text(s, 1.88, y, 4.55, 0.52, desc, 8.2, RGBColor(0xF6, 0xF9, 0xFE), SOFT_INK, LINE, pad=0.07)
        y += 0.52
        if i < len(layers) - 1:
            textbox(s, 3.85, y - 0.03, 0.3, 0.16, "▼", 7, RGBColor(0xC6, 0xD4, 0xE8), align=PP_ALIGN.CENTER)
            y += 0.13
    boxed_text(s, 0.55, 5.75, 5.9, 0.85,
               "**读侧（提示）：**快照是派生数据，每轮重新装配，永不回写。\n**写侧（工具）：**只有 service 能改聚合。",
               8.5, RGBColor(0xF3, 0xF6, 0xFB), SOFT_INK, pad=0.10)

    textbox(s, 6.75, 1.42, 5.8, 0.28, "一次写事务的四个动作", 12, NAVY, True)
    steps = [
        ("STEP 1", "校验", "模式归属、预算、单槽位、前置条件——拒绝发生在写之前。"),
        ("STEP 2", "读", "取聚合与当前 version；读不到就不创建（除显式建档）。"),
        ("STEP 3", "改", "就地修改 + 事件入内存队列；不落盘，不影响其他读者。"),
        ("STEP 4", "写", "版本比对成功才提交；事件随新 revision 落库，失败整体回滚。"),
    ]
    pos = [(6.75, 1.78), (9.72, 1.78), (6.75, 2.75), (9.72, 2.75)]
    for (x, y), (k, h, body) in zip(pos, steps):
        rect(s, x, y, 2.82, 0.86, RGBColor(0xF6, 0xF9, 0xFE), LINE)
        textbox(s, x + 0.10, y + 0.06, 2.6, 0.16, k, 7, BLUE, True)
        textbox(s, x + 0.10, y + 0.22, 2.6, 0.22, h, 10, NAVY, True)
        textbox(s, x + 0.10, y + 0.46, 2.6, 0.36, body, 7.5, SOFT_INK)
    textbox(s, 6.75, 3.78, 5.8, 0.28, "一条聚合，七张旁路表", 12, NAVY, True)
    boxed_text(s, 6.75, 4.12, 5.8, 1.05,
               "**真相：**paths（聚合 JSON + version）。\n**旁路：**事件 · 题目事务 · 题库 · 会话与消息 · 题卷与题目。\n旁路表可从聚合重建，但从不反向写回。",
               8.5, RGBColor(0xF3, 0xF6, 0xFB), SOFT_INK, pad=0.10)
    boxed_text(s, 6.75, 5.35, 5.8, 1.20,
               "**23 个工具 = 大纲 2 + 学习评估 9 + 共享 12**\n预算：工具 ≤6 · 轮数 ≤6 · 连续追问 ≤3 · 同题提示 ≤2。\n条件挂载：未绑定知识库不挂 rag，无来源索引不挂 read_source。",
               8.5, PALE_AMBER, RGBColor(0x8A, 0x6A, 0x3F), RGBColor(0xEB, 0xD3, 0xB4), pad=0.10)

    # ---------------- 7 tech table ----------------
    s = slide_base(prs, "03 技术选型", "每个选型都换来一个结构性质，也带来一个代价",
                   "“设想 → 现状 → 换来了什么 → 代价/待验证”，最后一列是第三部分要回答的问题。", 7)
    rows = [
        ("界面与交互", "Next.js 三模式工作区", "Next.js 14 + Tailwind 语义令牌", "三层动线；状态不只靠颜色", "暗色主题、移动端编辑"),
        ("回合与智能体", "通用 Agent Loop", "自写 Loop + 23 工具 + 四类结束原因", "事件顺序可断言；成本有上界", "提示与工具契约漂移"),
        ("判定与调度", "规则引擎 + SM-2", "domain 纯函数，零模型调用", "离线可逐值断言；now 显式传入", "0.9 / 0.90 阈值标定"),
        ("存储与一致性", "SQLite + CAS", "单聚合 JSON + version CAS + 8 张表", "读一次拿全量；事件同 revision 对账", "60 个知识点时的写放大"),
        ("模型接入", "供应商无关", "OpenAI 兼容 SSE + 脚本化假客户端", "tool_calls 按 index 拼装；测试离线", "非兼容协议、工具选择准确率"),
    ]
    table(s, 0.55, 1.42, 12.2, rows, [1.65, 2.55, 3.1, 2.95, 1.95],
          ["层", "Part I 设想", "当前实现", "换来了什么", "代价 / 待验证"], row_h=0.70, header_h=0.36, font_size=8.8)
    textbox(s, 0.55, 6.62, 12.2, 0.3,
            "JDK 21 · Spring Boot 3.5 · Maven Wrapper｜SQLite（WAL，单写者）｜Jackson｜JUnit 5 + AssertJ｜数据目录 data/",
            8.5, GRAY)

    # ---------------- 8 container ----------------
    s = slide_base(prs, "03 容器架构", "两个进程、一个目录，模型是可拔掉的插件",
                   "单机本地优先：不配模型也能跑“出题 → 判分 → 掌握 → 复习”。", 8)
    rect(s, 2.30, 1.42, 6.05, 4.05, RGBColor(0xFB, 0xFD, 0xFF), RGBColor(0xC9, 0xD6, 0xE8))
    textbox(s, 2.48, 1.52, 3.0, 0.24, "MasteryPath 系统边界", 8.5, NAVY, True)
    rect(s, 0.55, 2.42, 1.55, 0.85, WHITE, RGBColor(0xC9, 0xD6, 0xE8))
    textbox(s, 0.62, 2.55, 1.42, 0.60, "**学习者**\n浏览器 · 本地管理员", 8.5, NAVY)
    rect(s, 3.05, 1.72, 4.55, 0.78, PALE_BLUE, RGBColor(0xBF, 0xD1, 0xEC))
    textbox(s, 3.18, 1.82, 4.3, 0.60, "**浏览器前端（Next.js 14）**\n只读投影：图集 / 仪表盘 / 题库 · 写回合：学习工作区", 8.2, NAVY)
    rect(s, 3.05, 2.95, 4.55, 0.92, PALE_BLUE, BLUE)
    textbox(s, 3.18, 3.04, 4.3, 0.76, "**后端应用（Spring Boot 3.5 / JDK 21）**\nREST · WS /ws · SSE ｜ TurnRunner · AgentLoop · 23 工具\nservice 唯一写入口 · domain 零 I/O 判定", 8.2, NAVY)
    rect(s, 3.05, 4.28, 4.55, 0.70, WHITE, RGBColor(0xC9, 0xD6, 0xE8))
    textbox(s, 3.18, 4.38, 4.3, 0.55, "**数据目录 data/（单写者）**\nlearning.db · chat.db · settings.json · knowledge_bases/", 8.0, NAVY)
    rect(s, 8.75, 1.85, 3.0, 0.95, PALE_AMBER, AMBER, dash=True)
    textbox(s, 8.88, 1.95, 2.75, 0.78, "**模型提供方（OpenAI 兼容）**\n对话 / 出题 / 定性评估\n**待验证：可替换 / 可缺省**", 8.0, DEEP_AMBER)
    rect(s, 8.75, 3.20, 3.0, 0.82, PALE_AMBER, AMBER, dash=True)
    textbox(s, 8.88, 3.30, 2.75, 0.65, "**嵌入服务（可选）**\n知识库向量化与检索\n**未配置时检索返回空**", 8.0, DEEP_AMBER)
    arrow(s, 2.12, 2.82, 3.02, 2.14)
    textbox(s, 2.05, 2.28, 0.95, 0.22, "操作", 7.5, RGBColor(0x4A, 0x6B, 0x9A), align=PP_ALIGN.CENTER)
    arrow(s, 5.32, 2.50, 5.32, 2.93)
    textbox(s, 5.40, 2.58, 1.3, 0.22, "HTTP · WS · SSE", 7.5, RGBColor(0x4A, 0x6B, 0x9A))
    arrow(s, 5.32, 3.87, 5.32, 4.26)
    textbox(s, 5.40, 3.94, 1.2, 0.22, "读写", 7.5, RGBColor(0x4A, 0x6B, 0x9A))
    arrow(s, 7.62, 2.30, 8.72, 2.30, AMBER, True)
    textbox(s, 7.72, 2.08, 1.05, 0.22, "HTTPS / JSON", 7.5, DEEP_AMBER)
    arrow(s, 7.62, 3.58, 8.72, 3.58, AMBER, True)
    textbox(s, 7.86, 3.36, 1.0, 0.22, "检索", 7.5, DEEP_AMBER)
    boxed_text(s, 0.55, 5.62, 12.2, 0.80,
               "**一次 WS 请求 = 1 次会话写入 + N 次模型调用（N ≤ 6）+ M 次写事务**：每个写工具最多 1 次 mutate，读工具 0 次。\n实线 已实现 · 虚线 待 PoC 验证",
               8.8, RGBColor(0xF3, 0xF6, 0xFB), SOFT_INK, pad=0.12)

    # ---------------- 9 interaction ----------------
    s = slide_base(prs, "03 交互设计", "把“不确定的生成”收束成一条有序事件流",
                   "回合的价值不在文本，而在六步之内状态如何变化；下面三条顺序是硬约束。", 9)
    flow = [
        ("01 装配", "读侧就绪", "快照 + 复盘 + 历史 + 工具表"),
        ("02 模型轮", "流式产出", "文本增量即时透传"),
        ("03 工具", "校验后执行", "先发 tool_call 再执行"),
        ("04 收口", "出题即结束", "turn_end(card) 收尾"),
        ("05 通知", "session_meta", "turn_end 之后推送"),
        ("06 落库", "best-effort", "失败不回滚已完成状态"),
    ]
    x = 0.55
    for i, (k, h, body) in enumerate(flow):
        rect(s, x, 1.40, 1.80, 0.78, PALE_BLUE, RGBColor(0xE1, 0xE9, 0xF6))
        textbox(s, x + 0.08, 1.45, 1.64, 0.14, k, 6.8, BLUE, True)
        textbox(s, x + 0.08, 1.59, 1.64, 0.20, h, 9.2, NAVY, True)
        textbox(s, x + 0.08, 1.80, 1.64, 0.30, body, 6.8, SOFT_INK)
        x += 1.80
        if i < 5:
            textbox(s, x, 1.64, 0.22, 0.24, "›", 12, RGBColor(0xB9, 0xCB, 0xE4), True, PP_ALIGN.CENTER)
            x += 0.22
    picture_fit(s, ASSETS / "s4-graded.png", 0.55, 2.40, 6.05, 2.85)
    textbox(s, 0.55, 5.27, 6.05, 0.24, "① 题卡不含答案　② 判分先于讲解　③ 作答是下一条消息", 8.2, GRAY)
    cons = [
        ("顺序约束 1", "作答先判分，再让导师开口", "携带 mastery_answer 的回合，先判分并以 tool_result 回传；模型看不到未判分的答案。"),
        ("顺序约束 2", "题卡是 tool_result 的投影", "question_card 从工具结果的 metadata 派生；投影剔除答案，事件与历史共用同一份卡片。"),
        ("顺序约束 3", "session_meta 在 turn_end 之后", "模式或路径可能被工具中途改掉；前端必须先收到“回合结束”，再收到“现在是什么模式”。"),
    ]
    y = 2.40
    for n, h, body in cons:
        rect(s, 6.80, y, 5.95, 0.88, WHITE, LINE)
        textbox(s, 6.92, y + 0.06, 5.7, 0.16, n, 7, BLUE, True)
        textbox(s, 6.92, y + 0.22, 5.7, 0.22, h, 9.8, NAVY, True)
        textbox(s, 6.92, y + 0.46, 5.7, 0.38, body, 7.5, SOFT_INK)
        y += 0.98
    boxed_text(s, 0.55, 5.62, 12.2, 0.80,
               "**失败路径：**保留已产出的文本与题卡 → 发 error(code, retryable)，**不发 turn_end**；题卷路径例外。\n四类正常结束原因：done / card / budget / max_rounds。",
               8.5, RGBColor(0xF3, 0xF6, 0xFB), SOFT_INK, pad=0.12)

    # ---------------- 10 status ----------------
    s = slide_base(prs, "04 原型现状与计划", "闭环已经跑通，假设还没有验",
                   "设计已落地为可运行系统；真正不确定的是“阈值、契约同步、生成质量、单机上限”。", 10)
    def panel(x, header, amber, items, footer_text):
        rect(s, x, 1.42, 5.95, 4.90, RGBColor(0xFC, 0xFD, 0xFF), LINE)
        rect(s, x, 1.42, 5.95, 0.38, AMBER if amber else NAVY, radius=0.04)
        textbox(s, x + 0.14, 1.49, 5.6, 0.26, header, 11, WHITE, True)
        y = 1.92
        for h, body in items:
            textbox(s, x + 0.14, y, 5.65, 0.22, h, 9.6, DEEP_AMBER if amber else NAVY, True)
            textbox(s, x + 0.14, y + 0.22, 5.65, 0.38, body, 7.8, SOFT_INK)
            y += 0.66
        boxed_text(s, x + 0.14, y + 0.02, 5.65, 0.62, footer_text, 7.6,
                   PALE_AMBER if amber else PALE_BLUE,
                   RGBColor(0x8A, 0x6A, 0x3F) if amber else RGBColor(0x4A, 0x6B, 0x9A),
                   pad=0.10)

    panel(0.55, "已完成并验证", False, [
        ("三模式闭环与回合协议", "大纲共创 → 讲与考 → 到期复习；事件顺序、失败保留、模式回写均有契约测试。"),
        ("23 个工具与两层门控", "调用期门控 + 会话准入（学习需已有大纲）；分类表 fail-closed，未归类启动失败。"),
        ("确定性判定与单聚合一致性", "判分 / 门槛 / SM-2 纯函数；CAS 重试、事件同 revision、判分幂等。"),
        ("题库、知识库、设置与评测", "题库标签与刷题、文件夹来源与检索、提供方探测、大纲离线评测。"),
    ], "证据：93 个测试文件 / 864 条用例 · G0–G7 八个 Gate · 投影契约与并发用例")
    panel(6.80, "待完成与待验证", True, [
        ("提示与工具契约的漂移", "提示包引用了 ask_user 与 visual 参数，但工具注册表与 schema 里没有——模型照做会得到 unknown tool。"),
        ("阈值标定", "0.9 / 0.90 / 0.60 / EF 2.5 都还是先验值，没有用真实作答校准。"),
        ("生成质量与练习卷可用率", "大纲 F1、有界性、无环；题卷结构合法率与“修复一次”的救回率。"),
        ("单机上限与密钥保护", "并发写、聚合写放大；无鉴权 + 明文 Key 的产品边界。"),
    ], "下一步：第三部分用 PoC 把右侧四条变成数字")

    # ---------------- 11 open questions ----------------
    s = slide_base(prs, "04 待验证的问题", "四个问题决定它是不是真的“好用”",
                   "每个问题都给出方法与判据——不达标就改设计，而不是改说法。", 11)
    qs = [
        ("01 产品价值", "0.9 的门槛，会不会让正常人卡住？", "收集 200 条真实作答，两人独立双盲标注，与引擎结论逐条比对。", "判据：一致率 ≥90% · 误判为正确 ≤2%"),
        ("02 契约同步", "提示与工具不同步时，模型会怎么退化？", "用脚本化模型故意调用不存在的能力，观察拒绝路径与回合是否继续。", "判据：0 次静默失败 · 拒绝文案可操作"),
        ("03 生成质量", "大纲与题卷守得住吗？", "30 个目标跑离线评测；20 次题卷生成统计合法率与修复救回率。", "判据：F1 ≥0.8 · 无环 · 题卷合法率 ≥90%"),
        ("04 架构上限", "单机单进程，能撑到什么程度？", "5 个并发写 + 持续读，观察 CAS 冲突率、写放大与读接口 P95。", "判据：P95 ≤300ms · 冲突重试率 <5%"),
    ]
    pos = [(0.55, 1.45), (6.72, 1.45), (0.55, 3.85), (6.72, 3.85)]
    for (x, y), (tag, h, body, m) in zip(pos, qs):
        textbox(s, x, y, 5.9, 0.22, tag, 8, BLUE, True)
        textbox(s, x, y + 0.26, 5.9, 0.62, h, 13, NAVY, True)
        textbox(s, x, y + 0.92, 5.9, 0.62, body, 8.5, SOFT_INK)
        textbox(s, x, y + 1.58, 5.9, 0.26, m, 8, GRAY)

    # ---------------- 12 thanks ----------------
    s = prs.slides.add_slide(prs.slide_layouts[6])
    bg = s.background.fill
    bg.solid()
    bg.fore_color.rgb = WHITE
    textbox(s, 0.9, 2.35, 10.0, 1.0, "感谢聆听！", 44, RGBColor(0x2B, 0x34, 0x44), True)
    rect(s, 0.9, 3.45, 0.95, 0.05, BLUE, radius=0)
    textbox(s, 0.9, 3.85, 11.0, 0.30, "王君逸 · 程攀 · 卫梓豪 · 王沛璁", 12, GRAY)
    textbox(s, 0.9, 4.25, 11.5, 0.26, "完整设计工件：docs/design/（9 份工件 · 15 条 ADR · 15 条风险 · 12 个未决问题）", 9, RGBColor(0xA7, 0xB0, 0xC0))
    textbox(s, 0.9, 7.05, 6.0, 0.22, "MasteryPath · 系统设计", 8, RGBColor(0xA7, 0xB0, 0xC0))
    textbox(s, 11.55, 7.05, 1.2, 0.22, "12 / 12", 8, RGBColor(0xA7, 0xB0, 0xC0), align=PP_ALIGN.RIGHT)

    prs.save(str(OUT))
    print(f"saved {OUT}")


if __name__ == "__main__":
    build()
