package top.nontage.jniil.test.target;

import top.nontage.jniil.annotations.InjectClassInfo;

@InjectClassInfo(anchorClassType = String.class)
public class ClassTarget {
    public static String testMethod() {
        return "Injected Successfully";
    }
}
