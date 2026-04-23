package item;

public class Vehicle extends Item {
    private int mileage; // số km đã đi
    public Vehicle(String id, String name, double price, int mileage) {
        super(id, name, price);
        this.mileage = mileage;
    }

    @Override
    public void showDetails() {
        System.out.println("[Vehicle] " + getName() + " | Odo: " + mileage + " km");
    }
}
    

