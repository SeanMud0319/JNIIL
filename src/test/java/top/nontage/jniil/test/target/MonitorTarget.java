package top.nontage.jniil.test.target;

public class MonitorTarget {

    private String name;
    private int age;
    private int balance;
    private boolean isActive;
    private int loginAttempts;

    public MonitorTarget() {
        this.name = "DefaultUser";
        this.age = 0;
        this.balance = 0;
        this.isActive = true;
        this.loginAttempts = 0;
    }

    public MonitorTarget(String name, int age) {
        this.name = name;
        this.age = age;
        this.balance = 0;
        this.isActive = true;
        this.loginAttempts = 0;
    }

    public MonitorTarget(String name, int age, int balance) {
        this.name = name;
        this.age = age;
        this.balance = balance;
        this.isActive = true;
        this.loginAttempts = 0;
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

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public void deposit(int amount) {
        if (amount <= 0) {
            System.out.println("[Target] Invalid deposit amount: " + amount);
            return;
        }
        this.balance += amount;
        System.out.println("[Target] Deposited: " + amount + ", New balance: " + balance);
    }

    public boolean withdraw(int amount) {
        if (amount <= 0) {
            System.out.println("[Target] Invalid withdraw amount: " + amount);
            return false;
        }
        if (amount > balance) {
            System.out.println("[Target] Insufficient balance. Requested: " + amount + ", Available: " + balance);
            return false;
        }
        this.balance -= amount;
        System.out.println("[Target] Withdrew: " + amount + ", New balance: " + balance);
        return true;
    }

    public int calculateBonus(int base, int multiplier) {
        int bonus = base * multiplier;
        if (bonus > 1000) {
            bonus = 1000;
        }
        System.out.println("[Target] Calculated bonus: " + bonus);
        return bonus;
    }

    public double calculateTax(double income, double rate) {
        double tax = income * rate;
        if (tax < 0) {
            tax = 0;
        }
        System.out.println("[Target] Calculated tax: " + tax);
        return tax;
    }

    public String processLogin(String username, String password) {
        loginAttempts++;
        System.out.println("[Target] Login attempt " + loginAttempts + " for user: " + username);

        if (loginAttempts > 3) {
            isActive = false;
            System.out.println("[Target] Account locked due to too many failed attempts");
            return "LOCKED";
        }

        if ("admin".equals(username) && "secret".equals(password)) {
            loginAttempts = 0;
            System.out.println("[Target] Login successful!");
            return "SUCCESS";
        }

        System.out.println("[Target] Login failed. Invalid credentials");
        return "FAILED";
    }

    public String getStatus() {
        return "User: " + name + ", Age: " + age + ", Balance: " + balance + ", Active: " + isActive;
    }

    public int getLoginAttempts() {
        return loginAttempts;
    }

    public void reset() {
        this.balance = 0;
        this.loginAttempts = 0;
        this.isActive = true;
        System.out.println("[Target] Reset to initial state");
    }

    private void internalProcess() {
        System.out.println("[Target] Internal process executed");
    }

    public void publicMethodWithPrivateCall() {
        System.out.println("[Target] Calling internal process...");
        internalProcess();
    }

    public static String staticHelper(String input) {
        return "Static helper: " + input;
    }

    public boolean complexCondition(int x, int y, boolean flag) {
        boolean result = (x > 0 && y > 0) || (flag && x + y > 10);
        System.out.println("[Target] Complex condition result: " + result);
        return result;
    }
}