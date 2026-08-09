#!/usr/bin/env python3
"""Move the player-position floor into MarketValuation (single source) and exclude staff from the market model."""
import re

MV = 'src/main/java/com/github/fmaiassistent/mcp/MarketValuation.java'
TOOLS = 'src/main/java/com/github/fmaiassistent/mcp/FmAiAssistentTools.java'


def edit(path, ops):
    raw = open(path, 'rb').read()
    nl = '\r\n' if b'\r\n' in raw else '\n'
    text = raw.decode('utf-8')
    had_trailing_nl = text.endswith('\n')
    lines = text.splitlines()
    for pattern, replacement in ops:
        rx = re.compile(pattern)
        for i, line in enumerate(lines):
            if rx.match(line):
                lines[i] = replacement
                break
        else:
            raise SystemExit(f'pattern not found in {path}: {pattern[:70]}')
    result = nl.join(lines)
    if had_trailing_nl:
        result += nl
    open(path, 'wb').write(result.encode('utf-8'))
    print(f'edited {path}')


edit(MV, [
    (r'^\s*public static final int MIN_BUCKET_SAMPLES = 5;$',
     '    public static final int MIN_BUCKET_SAMPLES = 5;\n'
     '    /** Minimum best-position score for a person to count as a player; staff/retired entries have none. */\n'
     '    public static final int MIN_PLAYER_POSITION_SCORE = 5;'),
    (r'^\s*if \(value\(player\.getAskingPrice\(\)\) <= 0\) \{$',
     '            if (value(player.getAskingPrice()) <= 0 || !hasPlayablePosition(player)) {'),
    (r'^\s*public static int bestPositionIndex\(PlayerEntity player\) \{$',
     '    /** People-table exports include staff/retired entries with no position attributes; real players always have positions. */\n'
     '    public static boolean hasPlayablePosition(PlayerEntity player) {\n'
     '        return AttributeDefinitions.POSITION_FIELDS.stream()\n'
     '                .map(FmAiAssistentTools::columnName)\n'
     '                .map(player::getColumnValue)\n'
     '                .filter(Number.class::isInstance)\n'
     '                .map(Number.class::cast)\n'
     '                .mapToInt(Number::intValue)\n'
     '                .max()\n'
     '                .orElse(0) >= MIN_PLAYER_POSITION_SCORE;\n'
     '    }\n'
     '\n'
     '    public static int bestPositionIndex(PlayerEntity player) {'),
])

raw = open(TOOLS, 'rb').read()
nl = '\r\n' if b'\r\n' in raw else '\n'
text = raw.decode('utf-8')
had_trailing_nl = text.endswith('\n')

# drop local constant
assert re.search(r'^    private static final int MIN_PLAYER_POSITION_SCORE = 5;$', text, re.MULTILINE)
text = re.sub(r'^    private static final int MIN_PLAYER_POSITION_SCORE = 5;\r?\n', '', text, count=1, flags=re.MULTILINE)

# delegate in candidate pool
assert re.search(r'^            if \(!hasPlayablePosition\(player\)\) \{$', text, re.MULTILINE)
text = re.sub(r'^            if \(!hasPlayablePosition\(player\)\) \{$',
              '            if (!MarketValuation.hasPlayablePosition(player)) {', text, count=1, flags=re.MULTILINE)

# remove the local helper block (doc comment + method + trailing blank line)
pattern = (r'^    /\*\* People-table exports include staff/retired entries with no position attributes; real players always have positions\. \*/\r?\n'
           r'    static boolean hasPlayablePosition\(PlayerEntity player\) \{\r?\n'
           r'        return bestPositionScore\(player\) >= MIN_PLAYER_POSITION_SCORE;\r?\n'
           r'    \}\r?\n\r?\n')
assert re.search(pattern, text, re.MULTILINE), 'helper block not found'
text = re.sub(pattern, '', text, count=1, flags=re.MULTILINE)

if had_trailing_nl:
    text = text.rstrip('\r\n') + nl
open(TOOLS, 'wb').write(text.encode('utf-8'))
print(f'edited {TOOLS}')