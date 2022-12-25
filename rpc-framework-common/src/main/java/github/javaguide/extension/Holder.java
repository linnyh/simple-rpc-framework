package github.javaguide.extension;

public class Holder<T> {

    private volatile T value; // 对其他线程可见

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
