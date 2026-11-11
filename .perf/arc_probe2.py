"""Focused probe: is `_uuid` the missing piece for x/space/wbi/arc/search?"""
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


def gen_uuid():
    return (f"{rand_hex(8)}-{rand_hex(4)}-{rand_hex(4)}-{rand_hex(4)}-{rand_hex(12)}"
            f"{random.randint(10000, 99999)}infoc")


def signed(params):
    raw = IMG_KEY + SUB_KEY
    mixin = "".join(raw[i] for i in MIXIN_TAB if i < len(raw))[:32]
    p = dict(params)
    p["wts"] = int(time.time())
    parts = []
    for key in sorted(p):
        value = "".join(c for c in str(p[key]) if c not in "!'()*")
        parts.append(f"{urllib.parse.quote(key) }={urllib.parse.quote(value)}")
    query = "&".join(parts)
    return f"{query}&w_rid={hashlib.md5((query + mixin).encode()).hexdigest()}"


def main():
    global IMG_KEY, SUB_KEY
    base = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
            "Origin": "https://www.bilibili.com"}
    spi = http("https://api.bilibili.com/x/frontend/finger/spi", base)
    b3 = (spi.get("data") or {}).get("b_3", "")
    b4 = (spi.get("data") or {}).get("b_4", "")
    nav = http("https://api.bilibili.com/x/web-interface/nav", base)
    wbi = (nav.get("data") or {}).get("wbi_img") or {}
    IMG_KEY = wbi.get("img_url", "").rsplit("/", 1)[-1].split(".")[0]
    SUB_KEY = wbi.get("sub_url", "").rsplit("/", 1)[-1].split(".")[0]

    uuid = gen_uuid()

    cases = [
        ("no cookie at all", {}, 1),
        ("buvid3 only", {"buvid3": b3}, 1),
        ("buvid3+b4", {"buvid3": b3, "buvid4": b4}, 1),
        ("buvid3+b4+_uuid", {"buvid3": b3, "buvid4": b4, "_uuid": uuid}, 1),
        ("... same, pn=2", {"buvid3": b3, "buvid4": b4, "_uuid": uuid}, 2),
        ("... same, pn=30", {"buvid3": b3, "buvid4": b4, "_uuid": uuid}, 30),
        ("_uuid only (no buvid)", {"_uuid": uuid}, 1),
    ]
    for label, cookies, pn in cases:
        url = ("https://api.bilibili.com/x/space/wbi/arc/search?"
               + signed({"mid": MID, "pn": pn, "ps": 30, "keyword": ""}))
        h = dict(base)
        if cookies:
            h["Cookie"] = "; ".join(f"{k}={v}" for k, v in cookies.items())
        body = http(url, h)
        data = body.get("data") or {}
        vlist = ((data.get("list") or {}).get("vlist") or [])
        page = data.get("page") or {}
        print(f"[{label:22}] code={body.get('code'):>5} vlist={len(vlist):>3} "
              f"count={page.get('count')} pn={page.get('pn')} ps={page.get('ps')}")
        time.sleep(3.0)


if __name__ == "__main__":
    main()
