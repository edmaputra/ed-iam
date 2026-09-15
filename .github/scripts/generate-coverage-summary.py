#!/usr/bin/env python3
"""
Generate a consolidated Markdown summary from JaCoCo CSV reports
and append it to GitHub Actions Step Summary ($GITHUB_STEP_SUMMARY).
"""

import csv
import glob
import os
import sys


def pct(covered: int, missed: int) -> float:
    total = covered + missed
    return (covered / total * 100.0) if total > 0 else 100.0


def status_badge(percentage: float) -> str:
    if percentage >= 80.0:
        return "🟢"
    elif percentage >= 60.0:
        return "🟡"
    else:
        return "🔴"


def find_csv_file(preferred_path: str) -> str | None:
    if os.path.exists(preferred_path):
        return preferred_path

    # Fallback: search for any jacoco.csv in the workspace
    candidates = glob.glob("**/target/site/jacoco*/**/jacoco.csv", recursive=True)
    if candidates:
        return candidates[0]
    return None


def generate_summary(csv_path: str) -> str:
    modules: dict[str, dict[str, int]] = {}
    total = {
        "inst_m": 0, "inst_c": 0,
        "br_m": 0, "br_c": 0,
        "line_m": 0, "line_c": 0,
        "meth_m": 0, "meth_c": 0,
        "classes": 0,
    }
    class_missed = []

    with open(csv_path, mode="r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            group = row.get("GROUP", "default").replace("ed-iam-starter/", "")
            if group not in modules:
                modules[group] = {
                    "inst_m": 0, "inst_c": 0,
                    "br_m": 0, "br_c": 0,
                    "line_m": 0, "line_c": 0,
                    "meth_m": 0, "meth_c": 0,
                    "classes": 0,
                }

            inst_m = int(row.get("INSTRUCTION_MISSED", 0))
            inst_c = int(row.get("INSTRUCTION_COVERED", 0))
            br_m = int(row.get("BRANCH_MISSED", 0))
            br_c = int(row.get("BRANCH_COVERED", 0))
            line_m = int(row.get("LINE_MISSED", 0))
            line_c = int(row.get("LINE_COVERED", 0))
            meth_m = int(row.get("METHOD_MISSED", 0))
            meth_c = int(row.get("METHOD_COVERED", 0))

            modules[group]["inst_m"] += inst_m
            modules[group]["inst_c"] += inst_c
            modules[group]["br_m"] += br_m
            modules[group]["br_c"] += br_c
            modules[group]["line_m"] += line_m
            modules[group]["line_c"] += line_c
            modules[group]["meth_m"] += meth_m
            modules[group]["meth_c"] += meth_c
            modules[group]["classes"] += 1

            total["inst_m"] += inst_m
            total["inst_c"] += inst_c
            total["br_m"] += br_m
            total["br_c"] += br_c
            total["line_m"] += line_m
            total["line_c"] += line_c
            total["meth_m"] += meth_m
            total["meth_c"] += meth_c
            total["classes"] += 1

            if inst_m > 0:
                c_pct = pct(inst_c, inst_m)
                class_name = f"{row.get('PACKAGE', '')}.{row.get('CLASS', '')}"
                class_missed.append((inst_m, inst_c, c_pct, group, class_name))

    tot_line_pct = pct(total["line_c"], total["line_m"])
    tot_br_pct = pct(total["br_c"], total["br_m"])
    tot_inst_pct = pct(total["inst_c"], total["inst_m"])
    tot_meth_pct = pct(total["meth_c"], total["meth_m"])

    lines = [
        "## 📊 Unified JaCoCo Code Coverage Summary",
        "",
        f"> **Overall Coverage**: {status_badge(tot_line_pct)} **{tot_line_pct:.1f}%** Lines ({total['line_c']}/{total['line_c'] + total['line_m']}) | "
        f"{status_badge(tot_br_pct)} **{tot_br_pct:.1f}%** Branches ({total['br_c']}/{total['br_c'] + total['br_m']}) | "
        f"{status_badge(tot_inst_pct)} **{tot_inst_pct:.1f}%** Instructions ({total['inst_c']}/{total['inst_c'] + total['inst_m']})",
        "",
        "### 📦 Module Breakdown",
        "",
        "| Module | Line Coverage | Branch Coverage | Instruction Coverage | Method Coverage | Classes | Status |",
        "| :--- | :--- | :--- | :--- | :--- | :---: | :---: |",
    ]

    for mod, data in sorted(modules.items()):
        l_pct = pct(data["line_c"], data["line_m"])
        b_pct = pct(data["br_c"], data["br_m"])
        i_pct = pct(data["inst_c"], data["inst_m"])
        m_pct = pct(data["meth_c"], data["meth_m"])
        badge = status_badge(l_pct)
        status_text = "Pass" if l_pct >= 80.0 else ("Warning" if l_pct >= 60.0 else "Fail")

        lines.append(
            f"| `{mod}` | {badge} {l_pct:.1f}% ({data['line_c']}/{data['line_c'] + data['line_m']}) | "
            f"{b_pct:.1f}% ({data['br_c']}/{data['br_c'] + data['br_m']}) | "
            f"{i_pct:.1f}% ({data['inst_c']}/{data['inst_c'] + data['inst_m']}) | "
            f"{m_pct:.1f}% ({data['meth_c']}/{data['meth_c'] + data['meth_m']}) | "
            f"{data['classes']} | {badge} {status_text} |"
        )

    lines.append(
        f"| **Total** | {status_badge(tot_line_pct)} **{tot_line_pct:.1f}%** ({total['line_c']}/{total['line_c'] + total['line_m']}) | "
        f"**{tot_br_pct:.1f}%** ({total['br_c']}/{total['br_c'] + total['br_m']}) | "
        f"**{tot_inst_pct:.1f}%** ({total['inst_c']}/{total['inst_c'] + total['inst_m']}) | "
        f"**{tot_meth_pct:.1f}%** ({total['meth_c']}/{total['meth_c'] + total['meth_m']}) | "
        f"**{total['classes']}** | {status_badge(tot_line_pct)} **{'Pass' if tot_line_pct >= 80.0 else 'Review'}** |"
    )

    if class_missed:
        class_missed.sort(key=lambda x: x[0], reverse=True)
        top_missed = class_missed[:10]
        lines.extend([
            "",
            "<details>",
            f"<summary>🔍 <b>Top {len(top_missed)} Classes with Missed Instructions (click to expand)</b></summary>",
            "",
            "| Module | Class | Coverage | Missed Instructions |",
            "| :--- | :--- | :--- | :---: |",
        ])
        for m_inst, c_inst, c_pct, mod, cls in top_missed:
            lines.append(f"| `{mod}` | `{cls}` | {c_pct:.1f}% ({c_inst}/{c_inst + m_inst}) | {m_inst} |")
        lines.append("</details>")

    lines.append("")
    return "\n".join(lines)


def main():
    preferred_csv = sys.argv[1] if len(sys.argv) > 1 else "ed-iam-starter/target/site/jacoco-aggregate/jacoco.csv"
    csv_file = find_csv_file(preferred_csv)

    if not csv_file:
        content = "## 📊 JaCoCo Code Coverage Summary\n\n> [!WARNING]\n> No JaCoCo coverage reports found.\n"
    else:
        content = generate_summary(csv_file)

    # Output to stdout
    print(content)

    # Output to GitHub Step Summary if running in GitHub Actions
    summary_path = os.getenv("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, mode="a", encoding="utf-8") as f:
            f.write(content)

    # Output to optional file path (e.g. for PR comments)
    if len(sys.argv) > 2:
        out_file = sys.argv[2]
        os.makedirs(os.path.dirname(os.path.abspath(out_file)), exist_ok=True)
        with open(out_file, mode="w", encoding="utf-8") as f:
            f.write(content)


if __name__ == "__main__":
    main()
