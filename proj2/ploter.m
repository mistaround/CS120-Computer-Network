clc;
clear;
y = load('test-dpkg.txt');
a = size(y, 1);
x = 1:a;
plot(x, y);