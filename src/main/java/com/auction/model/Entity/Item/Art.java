package item;

public class Art extends Item{
    private String artist; // tên nghệ sĩ
    public Art(String id, String name, double startingPrice, String artist) {
        super(id, name, startingPrice);
        this.artist = artist;
    }
    @Override
    public void showDetails() {
        System.out.println("[Art] " + getName() + " | Họa sĩ: " + artist);
    }
}
