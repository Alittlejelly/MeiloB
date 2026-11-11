"""Confirm: does the `.png` suffix in the wbi keys explain the -403?

The app stores imgUrl.substringAfterLast('/') verbatim, i.e.
`7cd084941338d15a7eaf9fd7f2b3b9c2.png`, while bbplayer (and the reference
implementation) strip the extension before building the mixin key.
"""
import hashlib
import json
import random
import time
import urllib.error
import urllib.parse
import urllib.request

APP_UA = ("Mozilla/5.0 (iPhone; CPU iPhone OS 14_0_1 like Mac OS X) "
          "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 BiliApp/6.66.0")

MIXIN_TAB = [
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
    26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
    20, 34, 44, 52,
]

MID = 946974
IMG_URL = ""
SUB_URL = ""


def http(url, headers):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return {"code": exc.code, "message": "HTTP " + str(exc.code)}


def mixin(img, sub):
    raw = img + sub
    return "".join(raw[i] for i in MIXIN_TAB if i < len(raw))[:32]


def signed(params, mix):
    p = dict(params)
    p["wts"] = int(time.time())
    parts = []
    for key in sorted(p):
        value = "".join(c for c in str(p[key]) if c not in "!'()*")
        parts.append(f"{urllib.parse.quote(key)}={urllib.parse.quote(value)}")
    query = "&".join(parts)
    return f"{query}&w_rid={hashlib.md5((query + mix).encode()).hexdigest()}"


SPI = {}


def call(label, query):
    url = f"https://api.bilibili.com/x/space/wbi/arc/search?{query}"
    h = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
         "Origin": "https://www.bilibili.com",
         "Cookie": f"buvid3={SPI.get('b_3', '')}; buvid4={SPI.get('b_4', '')}"}
    body = http(url, h)
    data = body.get("data") or {}
    vlist = ((data.get("list") or {}).get("vlist") or [])
    print(f"[{label:38}] code={body.get('code'):>5} vlist={len(vlist):>3} "
          f"msg={body.get('message')!r}")
    time.sleep(4.0)


def main():
    global SPI
    base = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
            "Origin": "https://www.bilibili.com"}
    spi = http("https://api.bilibili.com/x/frontend/finger/spi", base)
    SPI = spi.get("data") or {}
    nav = http("https://api.bilibili.com/x/web-interface/nav",
               dict(base, Cookie=f"buvid3={SPI.get('b_3', '')}"))
    wbi = (nav.get("data") or {}).get("wbi_img") or {}
    img_url = wbi.get("img_url", "")
    sub_url = wbi.get("sub_url", "")
    print("img_url =", img_url)
    print("sub_url =", sub_url)

    img_ok = img_url.rsplit("/", 1)[-1].split(".")[0]
    sub_ok = sub_url.rsplit("/", 1)[-1].split(".")[0]
    img_bug = img_url.rsplit("/", 1)[-1]
    sub_bug = sub_url.rsplit("/", 1)[-1]
    print("ok  keys:", img_ok, sub_ok)
    print("bug keys:", img_bug, sub_bug)
    time.sleep(2)

    params = {"mid": MID, "pn": 1, "ps": 30, "keyword": ""}
    call("mixin from extension-stripped keys", signed(params, mixin(img_ok, sub_ok)))
    call("mixin from keys WITH .png (app bug)", signed(params, mixin(img_bug, sub_bug)))


if __name__ == "__main__":
    main()
