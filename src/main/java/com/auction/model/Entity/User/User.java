package user;
import Model.Entity;

public abstract class User extends Entity {
    private String email;
    private String password;

    public User(String id, String username, String email, String password) {
        super(id, username); // username được lưu vào biến name của Entity
        this.email = email;
        this.password = password;
    }
    public String getUsername() {
        return getName(); 
    }

    public void setUsername(String username) {
        setName(username);
    }
    // username , email , pass có thể sửa đc còn id thì ko sửa đc
    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

// hàm check email và pass có khớp với nhau hay ko
    public boolean isEmailMatch(String inputEmail) {
        return this.email.equalsIgnoreCase(inputEmail);
    }

    public boolean authenticate(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    public abstract void displayRole(); // Phương thức trừu tượng 
    
}