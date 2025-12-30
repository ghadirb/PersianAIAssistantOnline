#!/usr/bin/env python3
import base64
import hashlib
import urllib.request
from Crypto.Cipher import AES
from Crypto.Protocol.KDF import PBKDF2

GIST_URL = "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/5ec50251e01128e0ad8d380350a2002d5c5b585f/keys.txt"
PASSWORD = "12345"

def decrypt_keys(encrypted_base64, password):
    try:
        encrypted_data = base64.b64decode(encrypted_base64)
        
        salt = encrypted_data[:16]
        iv = encrypted_data[16:28]
        ciphertext = encrypted_data[28:]
        
        key = PBKDF2(password, salt, key_len=32, count=20000, hmac_hash_module=hashlib.sha256)
        
        cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
        plaintext = cipher.decrypt(ciphertext)
        
        return plaintext.decode('utf-8')
    except Exception as e:
        print(f"خطا: {e}")
        return None

print("دانلود...")
try:
    with urllib.request.urlopen(GIST_URL) as r:
        encrypted_text = r.read().decode('utf-8').strip()
    print(f"✅ دانلود ({len(encrypted_text)} bytes)")
except Exception as e:
    print(f"❌ {e}")
    exit(1)

print("رمزگشایی...")
decrypted = decrypt_keys(encrypted_text, PASSWORD)

if decrypted:
    print("✅ موفق!\n")
    print(decrypted)
    with open("decrypted_keys.txt", "w") as f:
        f.write(decrypted)
else:
    print("❌ ناموفق!")
