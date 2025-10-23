import urllib.request
import os

url = 'https://github.com/rhasspy/piper/releases/download/2023.11.14-2/voice-fa-ir-gyro-medium.tar.gz'
output = 'persian_tts.tar.gz'

print('دانلود مدل فارسی...')
urllib.request.urlretrieve(url, output)
print('✅ تمام!')
