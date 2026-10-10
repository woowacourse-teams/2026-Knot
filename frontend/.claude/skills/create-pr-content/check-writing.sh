#!/bin/bash
# writing-rules.md 금지 표현 검사기.
# 원본 훅과 같은 전처리·정규식을 쓰되,
# 훅 입력(JSON) 대신 파일 경로를 인자로 받고 .html 본문 텍스트도 검사한다.
#
# 사용법: bash check-writing.sh <파일.md|파일.html>
# 결과: 위반이 없으면 "OK"를 출력하고 0으로, 있으면 "줄번호: 내용"을 모두 출력하고 1로 종료한다.

FILE="$1"
[ -n "$FILE" ] || { echo "검사할 파일 경로를 인자로 주세요." >&2; exit 2; }
[ -f "$FILE" ] || { echo "파일이 없습니다: $FILE" >&2; exit 2; }

PATTERNS="$(cd "$(dirname "$0")" && pwd)/banned-patterns.txt"
[ -f "$PATTERNS" ] || { echo "패턴 파일이 없습니다: $PATTERNS" >&2; exit 2; }

# 마크다운: 훅과 같은 전처리. 코드 블록 내부 제외, 인라인 코드·HTML 주석 마커·URL 제거,
# 구분선/표 구분 행/frontmatter 경계(--- 등) 제외
strip_markdown() {
  awk '
    /^```/ { code = !code; next }
    code { next }
    {
      line = $0
      gsub(/`[^`]*`/, "", line)
      gsub(/<!--|-->/, "", line)
      gsub(/https?:\/\/[^ \t)>\]"\x27]+/, "", line)
      if (line ~ /^[ \t]*[-|: \t]+$/) next
      print NR ": " line
    }
  ' "$1"
}

# HTML: style·script·pre·code·svg 내부와 주석, 태그를 지우고 화면에 보이는 본문 텍스트만 남긴다.
# 지운 영역의 줄바꿈 수는 유지해 줄번호가 원본 파일과 맞도록 한다.
# 금지 문자(·, —)가 엔티티로 쓰여도 잡히도록 자주 쓰는 엔티티를 문자로 되돌린다.
read -r -d '' STRIP_HTML_PERL <<'PERL'
sub keep_lines { my $n = () = $_[0] =~ /\n/g; return "\n" x $n; }
s{<(style|script|pre|code|svg)\b[^>]*>.*?</\1\s*>}{keep_lines($&)}gsie;
s{<!--.*?-->}{keep_lines($&)}gse;
s{<[^>]*>}{my $k = keep_lines($&); $k eq "" ? " " : $k}gse;
s{&nbsp;|&#160;}{ }g;
s{&middot;|&#183;|&#xb7;}{\x{b7}}gi;
s{&mdash;|&#8212;|&#x2014;}{\x{2014}}gi;
s{&lt;}{<}g;
s{&gt;}{>}g;
s{&quot;}{"}g;
s{&#39;|&apos;}{\x27}g;
s{&amp;}{&}g;
PERL

strip_html() {
  perl -CSD -0777 -pe "$STRIP_HTML_PERL" "$1" | awk '
    {
      line = $0
      gsub(/https?:\/\/[^ \t)>\]"\x27]+/, "", line)
      if (line ~ /^[ \t]*$/) next
      if (line ~ /^[ \t]*[-|: \t]+$/) next
      print NR ": " line
    }
  '
}

case "$FILE" in
  *.md) TEXT=$(strip_markdown "$FILE") ;;
  *.html | *.htm) TEXT=$(strip_html "$FILE") ;;
  *) echo "지원하지 않는 확장자입니다(.md, .html만 검사): $FILE" >&2; exit 2 ;;
esac

VIOLATIONS=$(printf '%s\n' "$TEXT" | grep -E -f "$PATTERNS")

if [ -n "$VIOLATIONS" ]; then
  echo "금지 표현 위반($(printf '%s\n' "$VIOLATIONS" | wc -l | tr -d ' ')줄): $FILE"
  printf '%s\n' "$VIOLATIONS"
  exit 1
fi

echo "OK"
exit 0
