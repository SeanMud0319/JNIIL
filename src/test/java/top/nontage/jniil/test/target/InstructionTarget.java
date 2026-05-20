package top.nontage.jniil.test.target;

public class InstructionTarget {

    private int score = 0;
    private boolean isActive = false;

    public void processReward(int level) {
        System.out.println("[Target] Processing reward for level: " + level);

        if (level >= 10) {
            this.score += 500;
        } else {
            this.score += 100;
        }

        System.out.println("[Target] Current score: " + this.score);
    }

    public int calculateBonus(int base, int multiplier) {
        int result = (base + 10) * multiplier;

        if (result > 1000) {
            return 1000;
        }
        return result;
    }

    public void toggleStatus() {
        this.isActive = !this.isActive;
        System.out.println("[Target] Status toggled to: " + this.isActive);
    }

    public int getScore() {
        return score;
    }

    public boolean isActive() {
        return isActive;
    }
}