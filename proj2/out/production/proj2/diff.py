from random import randint
import sys
from struct import pack


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print('''Usage:
            python diff.py -g <length> ==> Genereate random file
            python diff.py file1 file2 ==> Compare file1 and file2
            ''')
    else:
        if sys.argv[1] == "-g":
            a = int(sys.argv[2])
            print("Length: %d" % a)
            with open("INPUT.txt", mode='wb') as f:
                for p in range(a):
                    f.write(pack('b', randint(-128, 127)))
        else:
            arr = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
            with open(sys.argv[1], mode='rb') as f:
                with open(sys.argv[2], mode='rb') as g:
                    fbuf = f.read()
                    gbuf = g.read()
                    if len(gbuf) > len(fbuf):
                        gbuf = gbuf[:len(fbuf)]
                    if len(fbuf) != len(gbuf):
                        print("File 1 length: %d" % len(fbuf))
                        print("File 2 length: %d" % len(gbuf))
                    else:
                        print("File length: %d" % len(fbuf))
                        cnt = 0
                        for i, (c, d) in enumerate(zip(fbuf, gbuf)):
                            if c != d:
                                cnt += 1
                                arr[i // 1000] += 1
                                # print("Char #%d not match, file1: %c, file2: %c" % (i, c, d))
                        print("Percentage: %d / %d = %f%%" % (cnt, len(fbuf), cnt * 100 / len(fbuf)))
                        print(arr)
