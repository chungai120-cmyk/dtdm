/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Server4;

public class RountingTable {

    public VirtualCircle table[];
    public int max=5;

    public RountingTable() {
        table = new VirtualCircle[5];

        VirtualCircle Server1 = new VirtualCircle("136.110.4.186", 2001, "Server1");
        VirtualCircle Server2 = new VirtualCircle("34.28.238.63", 2002, "Server2");
        VirtualCircle Server3 = new VirtualCircle("34.55.107.207", 2003, "Server3");
        VirtualCircle Server4 = new VirtualCircle("34.71.156.133", 2004, "Server4");
        VirtualCircle Server5 = new VirtualCircle("104.154.64.145", 2005, "Server5");


        table[0] = Server1;
        table[1] = Server2;
        table[2] = Server3;
        table[3] = Server4;
        table[4] = Server5;

        max = 5;

    }
}
