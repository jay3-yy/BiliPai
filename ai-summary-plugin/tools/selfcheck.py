#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地自测：没有 kotlinc/gradle 的环境里，用 Python 复刻核心纯逻辑并验证，
同时对所有 .kt 源文件做括号/字符串平衡检查。

用法：python3 tools/selfcheck.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src"

SENTENCE_END = set("。！？；!?.\n")
TIMESTAMP_OVERHEAD = 12


def segment(lines, budget=4500, overlap=160):
    """与 SubtitleSegmenter.kt 等价的实现。"""
    assert budget >= 500
    # 与 Kotlin 版一致：非法组合自动收敛，而不是抛异常
    overlap = max(0, min(overlap, budget // 2 - 1))
    if not lines:
        return []
    chunks = []
    cursor = 0
    guard = 0
    while cursor < len(lines):
        guard += 1
        if guard > 500:
            break
        start = cursor
        chars = 0
        end = cursor
        while end < len(lines) and chars < budget:
            chars += len(lines[end]["content"]) + TIMESTAMP_OVERHEAD
            end += 1
        cut = end
        if end < len(lines):
            min_keep = max(1, int((end - start) * 0.6))
            for p in range(end - 1, start + min_keep - 1, -1):
                if lines[p]["content"].strip()[-1:] in SENTENCE_END:
                    cut = p + 1
                    break
        slice_ = lines[start:cut]
        chunks.append({
            "index": len(chunks),
            "startSec": slice_[0]["from"],
            "endSec": slice_[-1]["to"],
            "lines": slice_,
        })
        # 已处理到结尾：直接结束，避免重叠回溯在末尾再切出重复分段
        if cut >= len(lines):
            break
        back = 0
        p = cut - 1
        while p > start and back < overlap:
            back += len(lines[p]["content"])
            p -= 1
        cursor = max(p + 1, start + 1)
    for c in chunks:
        c["total"] = len(chunks)
    return chunks


def parse_time(v):
    """与 ResultParser.parseTime 等价。"""
    if isinstance(v, (int, float)):
        return float(v) if v >= 0 else None
    if not isinstance(v, str):
        return None
    t = v.strip().strip("[]（）()")
    try:
        return float(t)
    except ValueError:
        pass
    parts = t.split(":")
    try:
        nums = [float(x.strip()) for x in parts]
    except ValueError:
        return None
    if len(nums) == 2:
        return nums[0] * 60 + nums[1]
    if len(nums) == 3:
        return nums[0] * 3600 + nums[1] * 60 + nums[2]
    return None


def extract_json(raw):
    """与 ResultParser.extractJson 等价。"""
    t = raw.strip()
    m = re.search(r"```(?:json)?\s*([\s\S]*?)```", t, re.I)
    cand = (m.group(1).strip() if m and m.group(1).strip().startswith("{") else t)
    a, b = cand.find("{"), cand.rfind("}")
    return cand[a:b + 1] if a >= 0 and b > a else None


def check_brackets(path):
    """粗略静态检查：括号平衡 + 字符串闭合（忽略注释里的括号）。"""
    text = path.read_text(encoding="utf-8")
    stack = []
    pairs = {")": "(", "]": "[", "}": "{"}
    i = 0
    in_line_comment = False
    in_block_comment = False
    in_string = False
    in_char = False
    while i < len(text):
        c = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if in_line_comment:
            if c == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if c == "*" and nxt == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_char:  # Kotlin 字符字面量，例如 '[' 或 '\\''
            if c == "\\":
                i += 2
                continue
            if c == "'":
                in_char = False
            i += 1
            continue
        if in_string:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_string = False
            i += 1
            continue
        if c == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue
        if c == "/" and nxt == "*":
            in_block_comment = True
            i += 2
            continue
        if c == '"':
            in_string = True
            i += 1
            continue
        if c == "'":
            in_char = True
            i += 1
            continue
        if c in "([{":
            stack.append((c, i))
        elif c in ")]}":
            if not stack or stack[-1][0] != pairs[c]:
                return f"括号不匹配 {c!r} @ offset {i}"
            stack.pop()
        i += 1
    if in_string:
        return "字符串未闭合"
    if stack:
        return f"未闭合括号 {stack[-1][0]!r} @ offset {stack[-1][1]}"
    return None


def main():
    failures = []

    # --- 分段逻辑 ---
    lines = [
        {"from": i * 3.0, "to": i * 3.0 + 2.8,
         "content": f"第{i}句" + "内" * 20 + "。"}
        for i in range(200)
    ]
    assert segment([]) == [], "空输入应返回空"
    one = segment(lines[:3], 4500, 160)
    assert len(one) == 1 and one[0]["total"] == 1, "短字幕应为单段"
    many = segment(lines, 600, 60)
    assert len(many) > 1, "长字幕应被切分"
    assert all(c["total"] == len(many) for c in many), "total 回填错误"
    for idx, c in enumerate(many):
        assert c["index"] == idx
        assert c["lines"], "分段不能为空"
        assert c["startSec"] <= c["endSec"]
        if idx:
            assert c["startSec"] >= many[idx - 1]["startSec"], "重叠导致起点倒退"
    covered = {ln["from"] for c in many for ln in c["lines"]}
    assert len(covered) >= len(lines) - 1, "重叠切分丢了字幕行"
    print(f"[OK] 分段器：200 行 -> {len(many)} 段，覆盖 {len(covered)}/{len(lines)} 行，无死循环")

    # 非法 overlap 组合应被收敛，而不是抛异常
    coerced = segment(lines, 1000, 900)
    assert len(coerced) > 1 and all(c["lines"] for c in coerced)
    print("[OK] 分段器：overlap >= budget/2 时自动收敛，不抛异常")

    # --- 时间戳 ---
    cases = {"06:12": 372.0, "1:02:03": 3723.0, "42": 42.0, "[01:02]": 62.0, 42: 42.0}
    for k, want in cases.items():
        got = parse_time(k)
        assert got is not None and abs(got - want) < 1e-6, f"parse_time({k!r})={got}, 期望 {want}"
    assert parse_time("不是时间") is None
    print("[OK] 时间戳解析：mm:ss / hh:mm:ss / 纯数字 / 方括号 / 非法输入")

    # --- JSON 容错 ---
    fenced = '好的\n```json\n{"overview":"讲了三件事","chapters":[]}\n```\n'
    obj = json.loads(extract_json(fenced))
    assert obj["overview"] == "讲了三件事"
    assert extract_json("没有 JSON 的纯文本") is None
    print("[OK] 结果解析：代码围栏 JSON 提取 + 纯文本兜底")

    # --- 静态检查 ---
    kt_files = sorted(SRC.rglob("*.kt"))
    assert kt_files, "没找到 .kt 文件"
    for f in kt_files:
        err = check_brackets(f)
        if err:
            failures.append(f"{f.relative_to(ROOT)}: {err}")
    print(f"[OK] 静态检查：{len(kt_files)} 个 Kotlin 文件，括号/字符串平衡"
          if not failures else "[FAIL] 静态检查发现问题")

    if failures:
        for f in failures:
            print("  -", f, file=sys.stderr)
        return 1
    print("\n全部自测通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
