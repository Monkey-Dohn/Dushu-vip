#!/usr/bin/env python3
"""
申怡读书(com.dyw) 登录自动化：从手机短信箱读验证码 -> 调登录接口 -> 打印账号态。

前提：设备已 root（读 /data 短信库需要 su），且验证码能正常下发。

用法:
    python tools/auto_login.py 13950290200 [--serial f015e049]
    python tools/auto_login.py 13950290200 --brute 0000-9999   # 对已下发且未过期的码做枚举（见下方说明）

说明:
1. 登录接口 POST https://app.shenyiedu.com/appv2/login/appMultiDeviceLogin
   走 ServerApi.f()，**不带签名**，body 字段：
   userName / authCode / type / imageUrl / nickName / unionId /
   source(int) / channelSource(String) / deviceType / deviceId
2. 发送验证码 POST /appv2/user/login/v2/sendAuthCode —— 需要本地 SignResult(ts+sign)，不适合重放。
3. 实测（2026-11-16）：服务端校验链路报阿里云 SDKError MissingAccessToken，
   此时任何验证码都返回 400，枚举没有意义。
"""
import argparse
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

LOGIN_URL = "https://app.shenyiedu.com/appv2/login/appMultiDeviceLogin"
HDRS = {"Content-Type": "application/json", "User-Agent": "okhttp/3.12.13"}


def adb(serial, *args, timeout=60):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")


def android_id(serial):
    r = adb(serial, "shell", "settings", "get", "secure", "android_id")
    v = (r.stdout or "").strip()
    return v or "unknown"


def read_last_sms(serial):
    """读最近一条包含 4~6 位数字的短信正文（需要 root）"""
    r = adb(serial, "shell", "su", "-c",
            "content query --uri content://sms/inbox --projection body:date 2>/dev/null | tail -20")
    text = r.stdout or ""
    codes = []
    for line in text.splitlines():
        m = re.search(r"(\d{4,6})", line)
        if m and ("申怡" in line or "验证码" in line or "code" in line.lower() or True):
            codes.append(m.group(1))
    return codes[-1] if codes else None


def login(phone, code, sid):
    body = {
        "userName": phone, "authCode": code, "type": "1",
        "imageUrl": "", "nickName": "", "unionId": "",
        "source": 0, "channelSource": "0",
        "deviceType": "Android-device", "deviceId": sid,
    }
    req = urllib.request.Request(LOGIN_URL, data=json.dumps(body).encode(),
                                 method="POST", headers=HDRS)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return -1, repr(e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("phone")
    ap.add_argument("--serial", default=None)
    ap.add_argument("--brute", default=None, help="如 0000-9999")
    a = ap.parse_args()

    sid = android_id(a.serial)
    print(f"device android_id={sid}")

    if a.brute:
        lo, hi = a.brute.split("-")
        start, end = int(lo), int(hi)
        width = len(lo)
        for frm in range(start, end + 1):
            code = str(frm).zfill(width)
            st, body = login(a.phone, code, sid)
            flag = "OK" if '"code":200' in body else ".."
            print(f"  {code} -> {st} {flag} {body[:110]}")
            if '"code":200' in body:
                print("命中:", body[:400])
                return
            time.sleep(0.4)
        print("枚举结束，未命中")
        return

    code = read_last_sms(a.serial)
    if not code:
        print("没在短信箱里读到验证码（检查 root / 短信是否到达）")
        sys.exit(2)
    print(f"读到验证码: {code}")
    st, body = login(a.phone, code, sid)
    print(f"登录 -> {st} {body[:400]}")


if __name__ == "__main__":
    main()
