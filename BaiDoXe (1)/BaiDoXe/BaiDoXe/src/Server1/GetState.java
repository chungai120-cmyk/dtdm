/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Server1;

import java.io.FileInputStream;

public class GetState {

    int currentCircle;

    public GetState(String servername) {
        try {
            FileInputStream fi = new FileInputStream(servername + "circle.txt");
            currentCircle = fi.read();
        } catch (Exception ex) {
            currentCircle = 0;
        }
    }

    public int getCurrentCircle() {
        return currentCircle;
    }

    public void sendUpdate(String dest, int port, String name) {
        String jeton = "";
        if (name.endsWith("1")) {
            jeton = "10000";
        } else if (name.endsWith("2")) {
            jeton = "01000";
        } else if (name.endsWith("3")) {
            jeton = "00100";
        } else if (name.endsWith("4")) {
            jeton = "00010";
        } else {
            jeton = "00001";
        }
        try {
            if (currentCircle < 1) {
                return;
            }
            Connect co = new Connect(dest, port, name);
            co.connect();
            co.requestServer(jeton + name + "Update" + (currentCircle));
            co.shutdown();
        } catch (Exception ex) {
        }
    }
}
