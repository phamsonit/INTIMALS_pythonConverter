"""
class 3
"""
class Class3:

    def method1(self, a, b):
        re = 0
        for i in range(a):
            re = i + b
        return re

    def method2(self, a, b):
        re = 0
        for i in range(a, a+b):
            if i % 2 == 0:
                re = i + b
        return re


cla = Class3()
print(cla.method1(3, 4))
