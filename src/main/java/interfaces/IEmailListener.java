package interfaces;

import java.util.List;
import librerias.Email;

public interface IEmailListener {

    void onReceiptEmail(List<Email> emails);

}
