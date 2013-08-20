package edu.stanford.nlp.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * This application displays a JFrame that you can draw in.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public class XYPlotFrame extends JFrame {

  private static final long serialVersionUID = 4165506969288945899L;
  private Color[][] grid;
  private int xMax;
  private int yMax;

  private class MyJPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(xMax, yMax);
    }

    public MyJPanel() {
      super();
      setFont(new Font("TimesRoman", Font.PLAIN, 12));
      setBackground(Color.white);
      setForeground(Color.white);
    }

    @Override
    public void paint(Graphics g) {
      g.setColor(Color.white);
      g.drawRect(0, 0, xMax - 1, yMax - 1);
      for (int x = 0; x < xMax; x++) {
        for (int y = 0; y < yMax; y++) {
          if (grid[x][y] != null) {
            g.setColor(grid[x][y]);
            g.fillRect(x, y, 8, 8);
          }
        }
      }
    }

  }


  public XYPlotFrame(String str, int x, int y) {
    super(str);
    xMax = x;
    yMax = y;
    grid = new Color[x][y];
    JPanel jp = new MyJPanel();
    getContentPane().add(jp, BorderLayout.CENTER);
    setBounds(20, 20, xMax + 35, yMax + 35);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    setVisible(true);
  }

  public void addXY(int x, int y, Color c) {
    if (x >= 0 && x < xMax && y >= 0 && y < yMax) {
      if (c != Color.black) {
        grid[x][y] = c;
      }
    }
  }

  public static void main(String args[]) {
    XYPlotFrame jf = new XYPlotFrame("Test", 200, 100);
    jf.addXY(30, 50, Color.RED);
    jf.addXY(40, 80, Color.BLUE);
  }

}
