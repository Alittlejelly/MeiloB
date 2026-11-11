"""Which cookie in the app's set flips arc/search to -403?"""
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
        parts.append(f"{urllib.parse.quote(key)}={urllib.parse.quote(value)}")
    query = "&".join(parts)
    return f"{query}&w_rid={hashlib.md5((query + mixin).encode()).hexdigest()}"


def call(cookies, label, pn=1):
    url = ("https://api.bilibili.com/x/space/wbi/arc/search?"
           + signed({"mid": MID, "pn": pn, "ps": 30, "keyword": ""}))
    h = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
         "Origin": "https://www.bilibili.com"}
    if cookies:
        h["Cookie"] = "; ".join(f"{k}={v}" for k, v in cookies.items())
    body = http(url, h)
    data = body.get("data") or {}
    vlist = ((data.get("list") or {}).get("vlist") or [])
    print(f"[{label:34}] code={body.get('code'):>5} vlist={len(vlist):>3} "
          f"msg={body.get('message')!r}")
    time.sleep(4.0)


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
    time.sleep(2)

    good = {"buvid3": b3, "buvid4": b4, "_uuid": gen_uuid()}
    call(good, "good buvid3/4 + _uuid")

    call(dict(good, buvid3="garbage-not-a-buvid"), "garbage buvid3")
    call(dict(good, buvid4="garbage"), "garbage buvid4")
    call(dict(good, **{"buvid3": b3.split("-")[0]}), "truncated buvid3")
    call(dict(good, SESSDATA="deadbeef%2C1700000000%2Cabcdef*g1"), "bogus SESSDATA")
    call(dict(good, sid="abc123"), "bogus sid")
    call(dict(good, **{"b_nut": "1700000000"}), "b_nut timestamp")
    call(dict(good, SESSDATA="deadbeef%2C1700000000%2Cabcdef*g1",
              DedeUserID="12345678", bili_jct="00" * 16,
              **{"DedeUserID__ckMd5": "00" * 8}), "full bogus login set")


if __name__ == "__main__":
    main()
