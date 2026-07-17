import sqlite3, json

conn = sqlite3.connect(r'C:\Users\ZRQ\.local\share\mimocode\mimocode.db')
cursor = conn.cursor()

# List recent sessions
print("=== RECENT SESSIONS ===")
cursor.execute("SELECT id, title, directory, time_created FROM session ORDER BY time_created DESC LIMIT 30")
rows = cursor.fetchall()
for r in rows:
    title = (r[1] or "(none)")[:60]
    directory = (r[2] or "(none)")[:50]
    print(f"{r[0]} | {r[3]} | {title} | dir={directory}")

# Filter for this project
print("\n=== SESSIONS FOR myjpa-plus ===")
cursor.execute("SELECT id, title, directory, time_created FROM session WHERE directory LIKE '%myjpa-plus%' ORDER BY time_created DESC LIMIT 20")
rows = cursor.fetchall()
for r in rows:
    title = (r[1] or "(none)")[:80]
    print(f"{r[0]} | {r[3]} | {title}")

conn.close()
