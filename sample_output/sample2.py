"""
class 2
"""
class Class2:

    def method1(self, a, b, c):
        if a > b:
            a -= b
        else:
            b -= a
        return a/b-c

    def method2(self, a, b):
        if a > b:
            a -= b
        else:
            a += b
        return a/b


"""
 comments on
 multiple lines
"""

cla = Class2()
print(cla.method2(3, 4))

""" single line comment """

appro = 0
j = 1
appro = appro + 4*(((-1)**j)/((2*j)+1))
