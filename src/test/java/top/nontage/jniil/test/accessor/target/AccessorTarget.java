package top.nontage.jniil.test.accessor.target;

public class AccessorTarget {

    private String name;
    private int age;
    private double salary;
    private boolean active;
    private static String staticField = "StaticValue";

    public AccessorTarget() {
        this.name = "Default";
        this.age = 0;
        this.salary = 0.0;
        this.active = true;
    }

    public AccessorTarget(String name, int age, double salary) {
        this.name = name;
        this.age = age;
        this.salary = salary;
        this.active = true;
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

    public double getSalary() {
        return salary;
    }

    public void setSalary(double salary) {
        this.salary = salary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public static String getStaticField() {
        return staticField;
    }

    public static void setStaticField(String value) {
        staticField = value;
    }

    public String greet(String greeting) {
        return greeting + ", " + name + "!";
    }

    public int calculate(int a, int b) {
        return a + b;
    }

    public void printInfo() {
        System.out.println("[Target] Name: " + name + ", Age: " + age + ", Salary: " + salary);
    }

    private String privateMethod(String input) {
        return "Private: " + input;
    }

    public String callPrivateMethod(String input) {
        return privateMethod(input);
    }
}