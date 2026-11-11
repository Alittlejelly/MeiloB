"""What does a bad w_rid / missing w_rid / stale wts return from arc/search?"""
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
IMG_KEY = ""
SUB_KEY = ""


def http(url, headers):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return {"code": exc.code, "message": "HTTP " + str(exc.code)}


def rand_hex(n):
    return "".join(random.choice("0123456789ABCDEF") for _ in range(n))


def mixin():
    raw = IMG_KEY + SUB_KEY
    return "".join(raw[i] for i in MIXIN_TAB if i < len(raw))[:32]


def query_of(params, wts):
    p = dict(params)
    p["wts"] = wts
    parts = []
    for key in sorted(p):
        value = "".join(c for c in str(p[key]) if c not in "!'()*")
        parts.append(f"{urllib.parse.quote(key)}={urllib.parse.quote(value)}")
    return "&".join(parts)


def call(label, query, note=""):
    url = f"https://api.bilibili.com/x/space/wbi/arc/search?{query}"
    h = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
         "Origin": "https://www.bilibili.com",
         "Cookie": f"buvid3={(SPI or {}).get('b_3', '')}; buvid4={(SPI or {}).get('b_4', '')}"}
    body = http(url, h)
    data = body.get("data") or {}
    vlist = ((data.get("list") or {}).get("vlist") or [])
    print(f"[{label:36}] code={body.get('code'):>5} vlist={len(vlist):>3} "
          f"msg={body.get('message')!r} {note}")
    time.sleep(4.0)


SPI = {}


def main():
    global IMG_KEY, SUB_KEY, SPI
    base = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
            "Origin": "https://www.bilibili.com"}
    spi = http("https://api.bilibili.com/x/frontend/finger/spi", base)
    SPI = spi.get("data") or {}
    nav = http("https://api.bilibili.com/x/web-interface/nav",
               dict(base, Cookie=f"buvid3={SPI.get('b_3', '')}"))
    wbi = (nav.get("data") or {}).get("wbi_img") or {}
    IMG_KEY = wbi.get("img_url", "").rsplit("/", 1)[-1].split(".")[0]
    SUB_KEY = wbi.get("sub_url", "").rsplit("/", 1)[-1].split(".")[0]
    time.sleep(2)

    params = {"mid": MID, "pn": 1, "ps": 30, "keyword": ""}
    now = int(time.time())

    q = query_of(params, now)
    good = hashlib.md5((q + mixin()).encode()).hexdigest()
    call("correct signature", f"{q}&w_rid={good}")

    call("wrong w_rid", f"{q}&w_rid={'0' * 32}")
    call("missing w_rid", q)
    call("wts 1h old, signature matching", (
        lambda qq: f"{qq}&w_rid={hashlib.md5((qq + mixin()).encode()).hexdigest()}"
    )(query_of(params, now - 3600)))
    call("signed WITHOUT keyword param", (
        lambda qq: f"{qq}&w_rid={hashlib.md5((qq + mixin()).encode()).hexdigest()}"
    )(query_of({"mid": MID, "pn": 1, "ps": 30}, now)))
    call("signed with ps as empty string", (
        lambda qq: f"{qq}&w_rid={hashlib.md5((qq + mixin()).encode()).hexdigest()}"
    )(query_of({"mid": MID, "pn": 1, "ps": 30, "keyword": ""}, now)).replace("keyword=&", ""))


if __name__ == "__main__":
    main()
