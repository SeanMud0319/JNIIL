package top.nontage.jniil.test.target;

public class StandardTarget {

    private String name;
    private int age;

    public StandardTarget(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public void printInfo() {
        System.out.println("Name: " + name + ", Age: " + age);
    }

    public int calculateBirthYear(int currentYear) {
        if (currentYear < 0) {
            throw new IllegalArgumentException("Invalid current year");
        }
        System.out.println("Processing birth year calculation for: " + name);
        return currentYear - age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}