package com.jinyan.assistant.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class test {

    public static void main(String[] args) {

        Scanner in = new Scanner(System.in);
        int st = 0;
        int dz = 0;
        int n = in.nextInt();
        for (int index = 1; index <= n; index ++) {
            switch (in.nextInt())  {
                case 1:
                    st ++;
                    break;
                case 2:
                    dz ++;
                    break;
                case 3:
                    if (st > dz) {
                        st ++;
                    } else if (dz > st) {
                        dz ++;
                    } else {
                        dz ++;
                        st ++;
                    }
                    break;
            }
        }
        System.out.println(st + " " + dz);
    }

//    public static void main(String[] args) {
//        Scanner in = new Scanner(System.in);
//        while (in.hasNextInt()) {// 注意，如果输入是多个测试用例，请通过while循环处理多个测试用例
//            int a = in.nextInt();
//            int b = in.nextInt();
//            System.out.println(a + b);
//        }
//    }

    public static void main2(String[] args) {
        Map<String, Person> personMap = new HashMap<>();


    }

    static class Person {

        private String personId;

        private Integer score;
    }
}
