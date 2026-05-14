package item;
import Model.Entity;
public abstract class Item extends Entity {
    private double startingPrice; // giá khởi điểm

    public Item(String id, String name, double startingPrice) {
        super(id, name); // Gửi id và name lên lớp cha Entity
        this.startingPrice = startingPrice;
    }
    public double getStartingPrice() {
        return startingPrice;
    }
    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }
    public abstract String showDetails();
}
