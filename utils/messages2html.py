#!/usr/bin/env python3
# Convert a Kontalk conversation to HTML
# Usage: ./messages2html.py messages.db phone_number [display_name]
# ** Does not support groups (yet) **
# HTML goes to standard output

import sys
import hashlib
import sqlite3
from datetime import datetime

DISPLAYNAME_ME = 'Me'
COLOR_ME = 'black'
COLOR_PEER = '#a94138'

def sha1(text):
    hashed = hashlib.sha1(text.encode('utf-8'))
    return hashed.hexdigest()

def dict_factory(cursor, row):
    d = {}
    for idx, col in enumerate(cursor.description):
        d[col[0]] = row[idx]
    return d

dbfile = sys.argv[1]
phone = sys.argv[2]
userId = sha1(phone)
try:
    displayName = sys.argv[3]
except:
    displayName = phone

conn = sqlite3.connect(dbfile)
conn.row_factory = dict_factory

c = conn.cursor()
c.execute('SELECT * FROM messages WHERE peer LIKE ? AND thread_id NOT IN (SELECT thread_id FROM groups) ORDER BY timestamp', (userId + '@%', ))

print( """
<!DOCTYPE html>
<html lang="en">
<head>
<title>Kontalk conversation with %s</title>

<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">

<style type="text/css">

* {
    padding: 0;
    margin: 0;
}

body {
    font-family: arial, helvetica, serif;
}

h1 {
    margin: 12px;
}

#conversation {
    width: 100%%;
}

.stamp {
    background-color: #f2f2f2;
    color: #20435c;
    font-weight: bold;
    text-align: center;
    padding: 8px;
}

.chat {
    padding: 4px;
    word-wrap: break-word;
}

.chat .incoming {
    color: %s;
}

.chat .outgoing {
    color: %s;
}

.peer {
    font-weight: bold;
}

</style>

</head>

<body>

<h1>Conversation with %s</h1>

<div id="conversation">

""" % (displayName, COLOR_PEER, COLOR_ME, displayName))

cur_date = None
row = c.fetchone()
while row:
    date = datetime.fromtimestamp(row['timestamp']/1000)
    date_fmt = date.strftime('%Y-%m-%d')
    if not cur_date or date_fmt != cur_date.strftime('%Y-%m-%d'):
        cur_date = date
        print( """
<div class="stamp">%s</div>
""" % (date_fmt, ))

    if row['direction'] == 0:
        name = displayName
        css = 'incoming'
    else:
        name = DISPLAYNAME_ME
        css = 'outgoing'

    if row['body_content']:
        content = row['body_content'].decode('utf-8')
    elif row['att_mime']:
        content = '[Media: ' + row['att_mime'] + ']'
    else:
        content = ''

    print( """
<div class="chat">
[%s] <span class="peer %s">%s</span>: %s
</div>
""" % (date.strftime('%H:%M:%S'), css, name, content))

    row = c.fetchone()


print( """
</table>

</body>
</html>
""")

conn.close()
