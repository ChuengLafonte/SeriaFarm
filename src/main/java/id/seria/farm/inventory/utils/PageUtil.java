package id.seria.farm.inventory.utils;

import java.util.ArrayList;
import java.util.List;

public class PageUtil {
    public static <T> List<T> getpageitems(List<T> list, int n, int n2) {
        int n3 = n * n2;
        int n4 = n3 - n2;
        List<T> arrayList = new ArrayList<>();
        for (int i = n4; i < n3; ++i) {
            try {
                arrayList.add(list.get(i));
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
        return arrayList;
    }

    public static boolean isPageValid(List<?> list, int n, int n2) {
        if (n <= 0) return false;
        int n3 = n * n2;
        int n4 = n3 - n2;
        return list.size() > n4;
    }
}
