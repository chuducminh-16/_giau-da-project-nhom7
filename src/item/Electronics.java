package item;

public class Electronics extends Item {
    private int warrantyPeriod; // thời gian bảo hành (tháng)
    public Electronics(String id, String name, double startingPrice, int warrantyPeriod) {
        super(id, name, startingPrice);
        this.warrantyPeriod = warrantyPeriod;
    }

    @Override
    public void showDetails() {
        System.out.println("[Electronics] " + getName() + " | Guarantee: " + warrantyPeriod + " tháng");
    }
}
    

