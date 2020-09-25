"""
class 4
"""
class Class4:

    def method1(self, a, b):
        re = 0
        for i in range(a):
            re = i + b
        print(re)

    def method2(self, a, b):
        re = 0
        for i in range(a):
            if i % 2 == 0:
                re = i + b
        print(re)


cla = Class4()
cla.method1(3, 4)
