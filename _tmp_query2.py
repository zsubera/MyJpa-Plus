import sqlite3, json

conn = sqlite3.connect(r'C:\Users\ZRQ\.local\share\mimocode\mimocode.db')
cursor = conn.cursor()

# Get user messages from recent sessions (last 7 days)
# Timestamp for 7 days ago: 2026-07-11 in epoch millis
import time
seven_days_ago = int((time.time() - 7*24*3600) * 1000)

# Get all sessions from this project
cursor.execute("""
    SELECT id, title, time_created 
    FROM session 
    WHERE directory LIKE '%myjpa-plus%' 
      AND time_created > ?
    ORDER BY time_created DESC
""", (seven_days_ago,))
sessions = cursor.fetchall()
print(f"=== Sessions from last 7 days: {len(sessions)} ===")
for s in sessions:
    print(f"  {s[0]} | {s[2]} | {(s[1] or '')[:60]}")

# Get user messages from these sessions (not checkpoint-writer)
print("\n=== User statements containing keywords ===")
for sid, title, tc in sessions:
    if 'checkpoint-writer' in (title or ''):
        continue
    cursor.execute("""
        SELECT m.id, json_extract(m.data, '$.role'), substr(
            (SELECT GROUP_CONCAT(json_extract(p.data, '$.text'), ' ')
             FROM part p WHERE p.message_id = m.id AND json_extract(p.data, '$.type') = 'text'),
        1, 500) as text_preview
        FROM message m
        WHERE m.session_id = ?
          AND json_extract(m.data, '$.role') = 'user'
        ORDER BY m.time_created
    """, (sid,))
    rows = cursor.fetchall()
    if rows:
        print(f"\n  Session: {sid} ({title})")
        for r in rows:
            text = r[2] or ''
            # Search for keywords
            keywords = ['always', 'never', 'remember', 'rule', 'decision', 'decided', 
                       'repeat', 'again', 'every time', 'workflow', '必须', '永远', '不要',
                       '记住', '规则', '决定', '每次', '工作流', '不修改', '不改变']
            if any(kw.lower() in text.lower() for kw in keywords):
                print(f"    [{r[0]}] {text[:300]}")

conn.close()
