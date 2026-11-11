#!/usr/bin/env python3
"""生成 语音计算器 图标：一个渐变圆角方块 + 白色麦克风"""
import math, struct, zlib, os

def png_write(path, w, h, rgba):
    raw = b''.join(b'\x00' + bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    hdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', hdr) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)

def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    L = vx * vx + vy * vy
    t = 0.0 if L == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / L))
    dx, dy = px - (ax + t * vx), py - (ay + t * vy)
    return math.hypot(dx, dy)

def rr_dist(px, py, cx, cy, hw, hh, r):
    dx = abs(px - cx) - (hw - r)
    dy = abs(py - cy) - (hh - r)
    return math.hypot(max(dx, 0), max(dy, 0)) + min(max(dx, dy), 0) - r

def mic_cov(x, y, s, ox, oy):
    """s=缩放, 麦克风在 192 画布上的几何，中心 (96,~100)"""
    px, py = (x - ox) / s, (y - oy) / s
    bodies = []
    bodies.append(seg_dist(px, py, 96, 68, 96, 100) - 22)      # 话筒主体
    bodies.append(seg_dist(px, py, 96, 132, 96, 150) - 5)      # 支架
    bodies.append(seg_dist(px, py, 74, 155, 118, 155) - 5)     # 底座
    d = math.hypot(px - 96, py - 106)                          # U 形环
    ring = abs(d - 34) - 5
    if py < 106:
        ring = 1e9
    bodies.append(ring)
    return min(bodies)

def render(size, bg_mode):
    S = 3  # 超采样
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            cov = 0.0
            bg = 0.0
            for sy in range(S):
                for sx in range(S):
                    px = x + (sx + 0.5) / S
                    py = y + (sy + 0.5) / S
                    u = px / size
                    v = py / size
                    d = mic_cov(px, py, size / 192.0, 0, 0)
                    if d < 0:
                        cov += 1.0
                    if bg_mode == 'full':
                        bg += 1.0
                    else:
                        rd = rr_dist(px, py, size / 2, size / 2, size / 2 - 1, size / 2 - 1, size * 0.23)
                        if rd < 0:
                            bg += 1.0
            n = S * S
            cov /= n
            bg /= n
            # 背景渐变 #2AD1C0 -> #2A7BE0
            t = (y / size)
            r0 = int(0x2A + (0x2A - 0x2A) * t)
            g0 = int(0xD1 + (0x7B - 0xD1) * t)
            b0 = int(0xC0 + (0xE0 - 0xC0) * t)
            # 白色麦克风
            R = int(r0 + (255 - r0) * cov)
            G = int(g0 + (255 - g0) * cov)
            B = int(b0 + (255 - b0) * cov)
            a = int(255 * (bg if bg_mode == 'rounded' else 1.0))
            i = (y * size + x) * 4
            out[i:i + 4] = bytes((R, G, B, a))
    return out

os.makedirs('/root/workspace/voicecalc/res/mipmap-xxhdpi', exist_ok=True)
os.makedirs('/root/workspace/voicecalc/res/mipmap-anydpi-v26', exist_ok=True)
os.makedirs('/root/workspace/voicecalc/res/drawable-nodpi', exist_ok=True)

png_write('/root/workspace/voicecalc/res/mipmap-xxhdpi/ic_launcher.png', 192, 192, render(192, 'rounded'))

# 自适应图标前景：432x432，麦克风缩小到安全区内
fg = bytearray(432 * 432 * 4)
body = render(432, 'full')
for y in range(432):
    for x in range(432):
        i = (y * 432 + x) * 4
        # 只保留麦克风（白色部分）作为前景
        r = body[i]
        # 用“接近白色”的程度作为 alpha
        alpha = 0 if (r < 120) else min(255, int((r - 120) * 255 / 135))
        fg[i:i + 4] = bytes((255, 255, 255, alpha))
png_write('/root/workspace/voicecalc/res/drawable-nodpi/ic_fg.png', 432, 432, fg)
print('icons done')
