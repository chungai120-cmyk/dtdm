/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Server2;

public class RountingTable {

    public VirtualCircle table[];
    public int max=5;

    public RountingTable() {
        table = new VirtualCircle[5];

        VirtualCircle Server1 = new VirtualCircle("34.28.67.13", 2001, "Server1");
        VirtualCircle Server2 = new VirtualCircle("34.60.160.83", 2002, "Server2");
        VirtualCircle Server3 = new VirtualCircle("136.114.153.146", 2003, "Server3");
        VirtualCircle Server4 = new VirtualCircle("34.41.250.212", 2004, "Server4");
        VirtualCircle Server5 = new VirtualCircle("34.42.41.5", 2005, "Server5");


        table[0] = Server1;
        table[1] = Server2;
        table[2] = Server3;
        table[3] = Server4;
        table[4] = Server5;

        max = 5;

    }
}