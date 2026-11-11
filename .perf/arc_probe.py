"""Probe x/space/wbi/arc/search with several cookie/UA configurations.

Goal: find out which part of the client identity the Gaia gate actually checks.
"""
import hashlib
import json
import random
import time
import urllib.parse
import urllib.request

APP_UA = ("Mozilla/5.0 (iPhone; CPU iPhone OS 14_0_1 like Mac OS X) "
          "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 BiliApp/6.66.0")
WEB_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0")

MIXIN_TAB = [
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
    26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
    20, 34, 44, 52,
]

MID = 946974


def get(url, headers):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8")), resp.headers.get_all("Set-Cookie") or []


def mixin_key(img_key, sub_key):
    raw = img_key + sub_key
    return "".join(raw[i] for i in MIXIN_TAB if i < len(raw))[:32]


def enc_wbi(params, img_key, sub_key):
    """bbplayer encWbi: strip !'()* from values, then urlencode."""
    mixin = mixin_key(img_key, sub_key)
    p = dict(params)
    p["wts"] = int(time.time())
    parts = []
    for key in sorted(p):
        value = "".join(c for c in str(p[key]) if c not in "!'()*")
        parts.append(f"{urllib.parse.quote(str(key), safe='')}={urllib.parse.quote(value, safe='')}")
    query = "&".join(parts)
    w_rid = hashlib.md5((query + mixin).encode()).hexdigest()
    return f"{query}&w_rid={w_rid}"


def rand_hex(n):
    return "".join(random.choice("0123456789ABCDEF") for _ in range(n))


def gen_uuid():
    return (f"{rand_hex(8)}-{rand_hex(4)}-{rand_hex(4)}-{rand_hex(4)}-{rand_hex(12)}"
            f"{random.randint(10000, 99999)}infoc")


def md5_fp():
    return hashlib.md5(f"fp={random.random()}".encode()).hexdigest()


DM = {
    "dm_img_list": "[]",
    "dm_img_str": "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
    "dm_cover_img_str": "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwICgweDAwMDAzRTlCKSBEaXJlY3QzRDExIHZzXzVfMCBwc181XzApLCBvciBzaW1pbGFy",
    "dm_img_inter": '{"ds":[],"wh":[0,0,0],"of":[0,0,0]}',
}


def probe(label, headers, cookies, extra=None, keys=(None, None)):
    img_key, sub_key = keys
    params = {"mid": MID, "pn": 1, "ps": 30, "keyword": ""}
    if extra:
        params.update(extra)
    query = enc_wbi(params, img_key, sub_key)
    url = f"https://api.bilibili.com/x/space/wbi/arc/search?{query}"
    h = dict(headers)
    cookie_header = "; ".join(f"{k}={v}" for k, v in cookies.items() if v)
    if cookie_header:
        h["Cookie"] = cookie_header
    try:
        body, _ = get(url, h)
        vlist = (((body.get("data") or {}).get("list") or {}).get("vlist") or [])
        count = ((body.get("data") or {}).get("page") or {}).get("count")
        print(f"[{label}] code={body.get('code')} msg={body.get('message')!r} "
              f"vlist={len(vlist)} count={count}")
        return body.get("code")
    except Exception as exc:  # noqa: BLE001
        print(f"[{label}] EXC {exc}")
        return None


def main():
    base_headers = {"User-Agent": APP_UA, "Referer": "https://www.bilibili.com/",
                    "Origin": "https://www.bilibili.com"}
    spi, _ = get("https://api.bilibili.com/x/frontend/finger/spi", base_headers)
    b3 = (spi.get("data") or {}).get("b_3", "")
    b4 = (spi.get("data") or {}).get("b_4", "")
    print("spi code=", spi.get("code"), "b_3=", b3[:24], "b_4=", b4[:24])

    nav, nav_cookie = get("https://api.bilibili.com/x/web-interface/nav", base_headers)
    wbi = (nav.get("data") or {}).get("wbi_img") or {}
    img_key = wbi.get("img_url", "").rsplit("/", 1)[-1].split(".")[0]
    sub_key = wbi.get("sub_url", "").rsplit("/", 1)[-1].split(".")[0]
    print("nav code=", nav.get("code"), "wbi img=", img_key[:12], "sub=", sub_key[:12])
    print("nav set-cookie:", [c.split(";")[0] for c in nav_cookie])
    keys = (img_key, sub_key)

    bare = {"buvid3": b3, "buvid4": b4}
    with_uuid = dict(bare, **{"_uuid": gen_uuid()})
    with_fp = dict(with_uuid, **{"buvid_fp": md5_fp()})

    probe("A appUA buvid3/4", base_headers, bare, keys=keys)
    probe("B +_uuid", base_headers, with_uuid, keys=keys)
    probe("C +buvid_fp", base_headers, with_fp, keys=keys)
    probe("D appUA full +dm", base_headers, with_fp, DM, keys=keys)
    web_headers = {"User-Agent": WEB_UA, "Referer": f"https://space.bilibili.com/{MID}",
                   "Origin": "https://space.bilibili.com"}
    probe("E webUA full +dm", web_headers, with_fp, DM, keys=keys)
    probe("F webUA full", web_headers, with_fp, keys=keys)


if __name__ == "__main__":
    main()
