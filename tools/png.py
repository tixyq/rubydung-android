import zlib, struct

def read_png(path):
    data = open(path,'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    idat = b''
    w=h=bd=ct=interlace=0
    palette=None
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos+4])[0]
        typ = data[pos+4:pos+8]
        chunk = data[pos+8:pos+8+ln]
        pos += 12+ln
        if typ == b'IHDR':
            w,h,bd,ct,comp,filt,interlace = struct.unpack('>IIBBBBB', chunk)
        elif typ == b'PLTE':
            palette = chunk
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
    raw = zlib.decompress(idat)
    if ct == 6:
        bpp = 4
    elif ct == 2:
        bpp = 3
    elif ct == 0:
        bpp = 1
    elif ct == 4:
        bpp = 2
    elif ct == 3:
        bpp = 1
    else:
        raise ValueError('ct %d' % ct)
    stride = w*bpp
    out = bytearray(h*stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i-bpp]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i-bpp] if i >= bpp else 0
                pp = a + b - c
                pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y*stride:(y+1)*stride] = line
        prev = line
    # convert to RGBA
    rgba = bytearray(w*h*4)
    if ct == 6:
        rgba = out
    elif ct == 2:
        for i in range(w*h):
            rgba[i*4:i*4+3] = out[i*3:i*3+3]
            rgba[i*4+3] = 255
    elif ct == 0:
        for i in range(w*h):
            v = out[i]
            rgba[i*4:i*4+4] = bytes((v,v,v,255))
    elif ct == 3:
        for i in range(w*h):
            idx = out[i]
            rgba[i*4:i*4+3] = palette[idx*3:idx*3+3]
            rgba[i*4+3] = 255
    return w,h,rgba

def write_png(path,w,h,rgba):
    raw = bytearray()
    stride = w*4
    for y in range(h):
        raw.append(0)
        raw += rgba[y*stride:(y+1)*stride]
    def chunk(t,d):
        c = struct.pack('>I',len(d)) + t + d
        c += struct.pack('>I', zlib.crc32(t+d)&0xffffffff)
        return c
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w,h,8,6,0,0,0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw),9))
    png += chunk(b'IEND', b'')
    open(path,'wb').write(png)

def crop(srcw,srch,src,x0,y0,cw,ch):
    out = bytearray(cw*ch*4)
    for y in range(ch):
        si = ((y0+y)*srcw + x0)*4
        out[y*cw*4:(y+1)*cw*4] = src[si:si+cw*4]
    return out

def paste(dstw,dsth,dst,srcw,srch,src,x0,y0,cw,ch):
    for y in range(ch):
        si = (y*srcw)*4
        di = ((y0+y)*dstw + x0)*4
        dst[di:di+cw*4] = src[si:si+cw*4]
