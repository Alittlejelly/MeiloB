"""Final check: replay the app's exact request after the fix.

Mirrors BiliWbi.keyFromUrl (extension stripped) + signedQuery (w_rid last) +
BiliInterceptor headers (iPhone BiliApp UA, web referer/origin) + the cookie
set BiliCookieStore.cookieHeader emits.
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
TAB = [
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
    26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
    20, 34, 44, 52,
]
MID = 946974


def http(url, headers):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return {"code": exc.code, "message": "HTTP " + str(exc.code)}


def key_from_url(url):
    """Same as BiliWbi.keyFromUrl: drop query, take last segment, drop extension."""
    return url.split("?")[0].rsplit("/", 1)[-1].split(".")[0]


def signed_query(params, img, sub):
    raw = img + sub
    mixin = "".join(raw[i] for i in TAB if i < len(raw))[:32]
    p = dict(params, wts=int(time.time()))
    parts = []
    for key in sorted(p):
        value = "".join(c for c in str(p[key]) if c not in "!'()*")
        parts.append(f"{urllib.parse.quote(key)}={urllib.parse.quote(value)}")
    q = "&".join(parts)
    return f"{q}&w_rid={hashlib.md5((q + mixin).encode()).hexdigest()}"


def main():
    base = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
            "Origin": "https://www.bilibili.com"}
    spi = (http("https://api.bilibili.com/x/frontend/finger/spi", base).get("data") or {})
    b3, b4 = spi.get("b_3", ""), spi.get("b_4", "")
    cookie = f"buvid3={b3}; buvid4={b4}"
    nav = http("https://api.bilibili.com/x/web-interface/nav", dict(base, Cookie=cookie))
    wbi = (nav.get("data") or {}).get("wbi_img") or {}
    img = key_from_url(wbi.get("img_url", ""))
    sub = key_from_url(wbi.get("sub_url", ""))
    print(f"buvid3={b3[:16]}... keys img={img[:10]}... sub={sub[:10]}...")
    time.sleep(6)

    for pn in (1, 2):
        url = ("https://api.bilibili.com/x/space/wbi/arc/search?"
               + signed_query({"mid": MID, "pn": pn, "ps": 30, "keyword": ""}, img, sub))
        body = http(url, dict(base, Cookie=cookie))
        data = body.get("data") or {}
        vlist = ((data.get("list") or {}).get("vlist") or [])
        page = data.get("page") or {}
        print(f"pn={pn} code={body.get('code')} vlist={len(vlist)} "
              f"count={page.get('count')} total_after={page.get('pn', 0) * page.get('ps', 0)}")
        time.sleep(10)


if __name__ == "__main__":
    main()
