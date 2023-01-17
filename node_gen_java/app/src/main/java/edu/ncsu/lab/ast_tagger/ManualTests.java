package edu.ncsu.lab.ast_tagger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManualTests {

    public static final Map<String, List<String>> TEST_IMPORTS = new HashMap<>() {{
        put("foreach", List.of("java.util.List", "java.util.Arrays"));

        put("brokenSymbolSolver", List.of("java.io.*", " java.util.*"));
    }};

    public static final Map<String, String> TEST_METHODS = new HashMap<>() {{
        put("if", """
                  public int testIf(String test) {
                      int num = Integer.parseInt(test);
                      if (num == 2) {
                          num--;
                          return num + 1;
                      } else if (num == 3) {
                          num++;
                          return 4;
                      } else {
                          num = 8;
                          return num;
                      }
                  }
                  """);
        put("for", """
                   public void testFor(int num) {
                       for (int i = 0; i < num; i++) {
                           System.out.println(i);
                       }
                       
                       for (int i = 0, j = 10; i < j; i++, j--) {}
                       
                       for (;;i++,j++){}
                       
                       int j = 0;
                       for (; j < num; j++) {
                           System.out.println(j);
                       }
                       
                       int k = 0;
                       for (; k < num;) {
                           System.out.println(k);
                           k++;
                       }
                       
                       int u = 0;
                       for (;;) {
                           System.out.println(u);
                           u++;
                       }
                   }
                   """);
        put("while", """
                     public void testWhile(int num) {
                         int i = 0;
                         while (i < num) {
                             System.out.println(i);
                             i++;
                         }
                         
                         while(true) {
                             System.out.println("Infinite loop");
                         }
                         
                         String result = "";
                         boolean control = true;
                         int index = 0;
                         int bread = 0;
                         int secondBread = 0;
                         while (control) {
                             bread = str.indexOf("bread", index);
                             if ((bread) + 5 == str.length()) {
                                 break;
                             } else if (bread == -1) {
                                 break;
                             }
                             index = bread + 5;
                             secondBread = str.indexOf("bread", index);
                             if (secondBread == -1) {
                                 break;
                             }
                             if (control) {
                                 result = str.substring(index, secondBread);
                             }
                         }
                     }
                     """);
        put("foreach", """
                       public void testForeach() {
                           int[] n = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
                           List<Integer> x = new ArrayList<Integer>();
                           for (int i : n) {
                               x.add(i);
                           }
                          
                           for (int i : x) {
                               System.out.println(i);
                               break;
                           }
                           
                           String test = "blue";
                           for (char character : test) {
                               System.out.println(character);
                           }
                       }
                       """);
        put("labeled", """
                       public boolean bobThere(String str) {
                           boolean isBalanced = false;
                           for (int i = 0; i < str.length(); i++) {
                               OUTER_LOOP: if (str.charAt(i) == 'x') {
                                   for (int j = i; j < str.length(); j++) {
                                       if (str.charAt(j) == 'y') {
                                           isBalanced = true;
                                           break OUTER_LOOP;
                                       } else {
                                           isBalanced = false;
                                       }
                                   }
                               }
                           }
                                       
                           String postB;
                           int index = -1;
                           loop: for (int i = -1; i <= str.length(); i++) {
                               if (str.startsWith("b")) {
                                   postB = str.substring(0);
                                   index = postB.indexOf("b");
                                   if (index == 1) {
                                       break loop;
                                   }
                               }
                           }
                           if (index == 1) {
                               return true;
                           } else {
                               return false;
                           }
                       }
                       """);
        put("switch", """
                      public String alarmClock(int day, boolean vacation) {
                          String alarm = "";
                          if (vacation) {
                              switch(day) {
                                  case 0:
                                      alarm = "off";
                                      break;
                                  case 1:
                                      alarm = "10:00";
                                      break;
                                  case 2:
                                      alarm = "10:00";
                                      break;
                                  case 3:
                                      alarm = "10:00";
                                      break;
                                  case 4:
                                      alarm = "10:00";
                                      break;
                                  case 5:
                                      alarm = "10:00";
                                      break;
                                  case 6:
                                      alarm = "off";
                                      break;
                              }
                          } else {
                              switch(day) {
                                  case 0:
                                      alarm = "10:00";
                                      break;
                                  case 1:
                                      alarm = "7:00";
                                      break;
                                  case 2:
                                      alarm = "7:00";
                                      break;
                                  case 3:
                                      alarm = "7:00";
                                      break;
                                  case 4:
                                      alarm = "7:00";
                                      break;
                                  case 5:
                                      alarm = "7:00";
                                      break;
                                  case 6:
                                      alarm = "10:00";
                                      break;
                                  default:
                                      alarm = "6:00";
                                      break;
                              }
                          }
                          return alarm;
                      }
                      """);
        put("return", """
                      public static boolean testReturn() {
                        int x = 12;
                        if (x == 13) {
                            return false;
                        }
                        
                        x = 7;
                        return true;
                      }
                                            """);
        put("break", """
                     public String testBreak(String str, String word) {
                      // find all appearances of word in str
                      // save indexes of these appearances
                      // split each part into a new substring
                      String str0 = "";
                      String str1 = "";
                      String plus = "";
                      String plus1 = "";
                      int y = 0;
                      while (str.contains(word)) {
                          int x = str.indexOf(word);
                          if (y == x) {
                              break;
                          }
                          str1 = str.substring(y, x);
                          for (int i = 0; i < str1.length(); i++) {
                              plus1 = plus1.concat("+");
                          }
                          plus = plus.concat(plus1);
                          plus = plus.concat(word);
                          str = str.substring(x + word.length());
                          y = x;
                          // return str0;
                      }
                      for (int i = 0; i < str.length(); i++) {
                          plus = plus.concat("+");
                      }
                      return plus;
                     }
                                          """);
        put("continue", """
                        public int[] zeroMax(int[] nums) {
                            int numsLength = nums.length;
                            int[] result = new int[numsLength];
                            for (int index = 0; index < numsLength; ++index) {
                                int currentValue = nums[index];
                                if (currentValue != 0) {
                                    result[index] = currentValue;
                                    continue;
                                }
                                int largestOdd = 0;
                                for (int oddIndex = index + 1; oddIndex < numsLength; ++oddIndex) {
                                    int currentOddValue = nums[oddIndex];
                                    if (currentOddValue % 2 > 0) {
                                        if (largestOdd < currentOddValue) {
                                            largestOdd = currentOddValue;
                                        }
                                    }
                                }
                                result[index] = largestOdd;
                            }
                            return result;
                        }
                        """);
        put("aggregateWhile", """
                              public int aggregate(int[] array)
                              {
                                  int sum = 0;
                                  int current = 0;
                                  while (current < array.length) {
                                      sum += array[current];
                                      current += 1;
                                  }
                                  return sum;
                              }
                                              
                              """);
        put("aggregateFor", """
                            public int aggregate(int[] array)
                            {
                                int sum = 0;
                                for (int i = 0; i < array.length; i += 1)
                                {
                                    sum += array[i];
                                }
                                return sum;
                            }
                            """);
        put("exceptionCatch", """
                              public void catchException()
                              {
                                try {
                                    throw new RuntimeException("Test");
                                }
                                catch (Exception e) {
                                    System.out.println("Voila");
                                }
                              }
                              """);
        put("throwUnknownMethod", """
                                  public void catchException()
                                  {
                                    try {
                                        throw UnknownMethod("Test");
                                    }
                                    catch (Exception e) {
                                        System.out.println("Voila");
                                    }
                                  }
                                  """);
        put("trFailGracefully", """
                                public int makeChocolate(int small, int big, int goal)
                                {
                                    big = 5 * big;
                                    int i;
                                    for (int n : i)
                                    {
                                        if (goal <= small) return goal;
                                        else if (goal == big) return 0;
                                        else if (goal == big / n) return 0;
                                        else
                                        {
                                            if (goal > big)
                                            {
                                                if (big + small >= goal)
                                                {
                                                    if (small >= goal - big)
                                                    {
                                                        if (big > small) return goal - big;
                                                        else return goal - small;
                                                    }
                                                    else return -1;
                                                }
                                                else return -1;
                                            }
                                            else return -1;
                                        }
                                    }
                                }
                                                                
                                                                
                                """);
        put("emptyBlockStmt", """
                              public int countEvens(int[] nums)
                              {
                                  int count = 0;
                                  for (int i = 0; i < nums.length; i++)
                                  {
                                      if (nums[i] % 2 == 0) count++;;
                                      {
                                        
                                      }
                                  }
                                  return count;
                              }
                              """);
        put("emptyIfStatement", """
                                public int roundSum(int a, int b, int c)
                                {
                                    int A = round10(a);
                                    int B = round10(b);
                                    int C = round10(c);
                                    return A + B + C;
                                }
                                                                
                                public int round10(int num)
                                {
                                    if (num < 10 && num < 5)
                                    {
                                        return 0;
                                    }
                                    else if (num <= 10 && num > 5)
                                    {
                                        return 10;
                                    }
                                    
                                    String number = "num";
                                    int rightMost = number.charAt(number.length());
                                    
                                    if (rightMost >= 5)
                                    {
                                        
                                    }
                                    
                                    //int result = Integer.parseInt(leftNum);
                                    return 0;
                                }
                                """);
        put("brokenSymbolSolver", """
                                  public static void main(String[] args) {
                                      userInterface();
                                  }
                                                                    
                                  public static void userInterface() {
                                      Scanner console = new Scanner(System.in);
                                      Scanner fileScanner = getInputScanner(console);
                                      System.out.println(flipLines(fileScanner));
                                  }
                                                                    
                                  public static String flipLines(Scanner input) {
                                      String result = "\\"";
                                      while (input.hasNextLine()) {
                                          String s1 = input.nextLine();
                                          String s2 = "\\"";
                                          if (input.hasNextLine()) {
                                              s2 = input.nextLine() + "\\n";
                                          }
                                          result += s2 + s1 + "\\n";
                                      }
                                      return result;
                                  }
                                                                    
                                  public static Scanner getInputScanner(Scanner console) {
                                      System.out.print("Enter a file name to process: ");
                                      File file = new File(console.next());
                                      while (!file.exists()) {
                                          System.out.print("File doesn't exist. " + "Enter a file name to process: ");
                                          file = new File(console.next());
                                      }
                                      Scanner fileScanner = null;
                                      try {
                                          fileScanner = new Scanner(file);
                                      } catch (FileNotFoundException e) {
                                          System.out.println("Input file not found. ");
                                          System.out.println(e.getMessage());
                                          System.exit(1);
                                      }
                                      return fileScanner;
                                  }
                                  """);
        put("blockStatementHandledIncorrectly", """
                                                /** Top Left point of rectangle. */
                                                private Point topLeft;
                                                /** Width of the rectangle. */
                                                private int width;
                                                /** Height of the rectangle. */
                                                private int height;
                                                /**
                                                 * Tests the Rectangles
                                                 * @param args command line arguments (not used)
                                                 */
                                                public static void main(String[] args) {
                                                    Point rectOneTop = new Point(10, 10);
                                                    Point rectTwoTop = new Point(20, 30);
                                                    MyRectangle rectOne = new MyRectangle(rectOneTop, 50, 40);
                                                    MyRectangle rectTwo = new MyRectangle(rectTwoTop, 80, 60);
                                                    System.out.println(rectOne.toString());
                                                    System.out.println(rectTwo.toString());
                                                    System.out.println();
                                                    System.out.println(rectOne.getTopLeft());
                                                    System.out.println(rectOne.getWidth());
                                                    System.out.println(rectOne.getHeight());
                                                    System.out.println(rectOne.getArea());
                                                    System.out.println(rectOne.getPerimeter());
                                                    System.out.println();
                                                    Point testOne = new Point(25, 50);
                                                    Point testTwo = new Point(101, 90);
                                                    System.out.println(rectOne.contains(testOne));
                                                    System.out.println(rectTwo.contains(testTwo));
                                                    System.out.println();
                                                    System.out.println(rectOne.union(rectTwo).toString());
                                                    System.out.println(rectOne.intersection(rectTwo).toString());
                                                    System.out.println();
                                                    rectOne.translate(30,50);
                                                    System.out.println(rectOne.toString());
                                                    rectOne.scale(1.73);
                                                    System.out.println(rectOne.toString());
                                                    rectTwo.scale(0.36);
                                                    System.out.println(rectTwo.toString());
                                                    System.out.println();
                                                    Point rectThreeTop = new Point(40, 60);
                                                    MyRectangle rectThree = new MyRectangle(rectThreeTop, 87, 69);
                                                    System.out.println(rectThree == rectOne);
                                                    System.out.println(rectThree.equals(rectOne));
                                                }
                                                /** Creates a rectangle with the given top left corner and the given width and height.
                                                 * @param topLeft **point at the top left of the rectangle**
                                                 * @param width **width of the rectangle**
                                                 * @param height **height of the rectangle**
                                                 */
                                                public MyRectangle(Point topLeft, int width, int height){
                                                    this.topLeft = topLeft;
                                                    this.width = width;
                                                    this.height = height;
                                                }
                                                /**
                                                 * Returns top left of the rectangle.
                                                 * @return topLeft **top left of the rectangle**
                                                 */
                                                public Point getTopLeft(){
                                                    return topLeft;
                                                }
                                                /**
                                                 * Returns width of the rectangle.
                                                 * @return width **width of the rectangle**
                                                 */
                                                public int getWidth(){
                                                    return width;
                                                }
                                                /**
                                                 * Returns height of the rectangle.
                                                 * @return height **height of the rectangle**
                                                 */
                                                public int getHeight(){
                                                    return height;
                                                }
                                                /**
                                                 * Returns area of the rectangle.
                                                 * @return area **area of the rectangle**
                                                 */
                                                public int getArea(){
                                                    int area = width * height;
                                                    return area;
                                                }
                                                /**
                                                 * Returns perimeter of the rectangle.
                                                 * @return perimeter **perimeter of the rectangle**
                                                 */
                                                public int getPerimeter(){
                                                    int perimeter = width * 2 + height * 2;
                                                    return perimeter;
                                                }
                                                /**
                                                 * Returns true if the rectangle contains the given point.
                                                 * @param point **point to be tested**
                                                 * @return true only if it contains the point given
                                                 */
                                                public boolean contains(Point point){
                                                    int x = point.x;
                                                    int y = point.y;
                                                    if (x < topLeft.x || y < topLeft.y){
                                                        return false;
                                                    }
                                                    if (x > topLeft.x + width || y > topLeft.y + height){
                                                        return false;
                                                    }
                                                    return true;
                                                }
                                                /**
                                                 * Returns the smallest rectangle that contains both this one and the other.
                                                 * @param other **other rectangle you want to use**
                                                 * @return union **rectangle that contains both rectangles**
                                                 */
                                                public MyRectangle union(MyRectangle other){
                                                    int otherStartX = other.getTopLeft().x;
                                                    int otherStartY = other.getTopLeft().y;
                                                    int otherEndX = otherStartX + other.getWidth();
                                                    int otherEndY = otherStartY + other.getHeight();
                                                    int startX = topLeft.x;
                                                    int startY = topLeft.y;
                                                    int endX = startX + width;
                                                    int endY = startY + height;
                                                    int topLeftX;
                                                    int topLeftY;
                                                    int bottomRightX;
                                                    int bottomRightY;
                                                    if (otherStartX < startX){
                                                        topLeftX = otherStartX;
                                                    } else {
                                                        topLeftX = startX;
                                                    }
                                                    if (otherStartY < startY){
                                                        topLeftY = otherStartY;
                                                    } else {
                                                        topLeftY = startY;
                                                    }
                                                    if (otherEndX > startX){
                                                        bottomRightX = otherEndX;
                                                    } else {
                                                        bottomRightX = endX;
                                                    }
                                                    if (otherEndY > startY){
                                                        bottomRightY = otherEndY;
                                                    } else {
                                                        bottomRightY = endY;
                                                    }
                                                    Point newTopLeft = new Point(topLeftX, topLeftY);
                                                    int newWidth = bottomRightX - topLeftX;
                                                    int newHeight = bottomRightY - topLeftY;
                                                    MyRectangle union = new MyRectangle(newTopLeft, newWidth, newHeight);
                                                    return union;
                                                }
                                                /**
                                                 * Returns the largest rectangle that is contained in both this and the other.
                                                 * @param other **another rectangle you want to use**
                                                 * @return intersection **rectangle that is contained in both**
                                                 */
                                                public MyRectangle intersection(MyRectangle other){
                                                    int startX = topLeft.x;
                                                    int startY = topLeft.y;
                                                    int intersectionX = 0;
                                                    int intersectionY = 0;
                                                    int endX = 0;
                                                    int endY = 0;
                                                    search:{
                                                        for (int i = 0; i < width; i++){
                                                            for (int j = 0; j < height; j++){
                                                                Point temp = new Point(startX + i, startY + j);
                                                                if (other.contains(temp)){
                                                                    intersectionX = temp.x;
                                                                    intersectionY = temp.y;
                                                                    break search;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    for (int i = 0; i < width; i++){
                                                        for (int j = 0; j < height; j++){
                                                            Point temp = new Point(startX + i, startY + j);
                                                            if (other.contains(temp)){
                                                                endX = temp.x;
                                                                endY = temp.y;
                                                            }
                                                        }
                                                    }
                                                    Point interTopLeft = new Point(intersectionX, intersectionY);
                                                    int interWidth = endX + 1 - intersectionX;
                                                    int interHeight = endY  + 1 - intersectionY;
                                                    MyRectangle intersection = new MyRectangle(interTopLeft, interWidth, interHeight);
                                                    return intersection;
                                                }
                                                /**
                                                 * Moves the rectangle a specific X and Y.
                                                 * @param deltaX **Horizontal change**
                                                 * @param deltaY ** Vertical change**
                                                 */
                                                public void translate(int deltaX, int deltaY){
                                                    Point newTopLeft = new Point(topLeft.x + deltaX, topLeft.y + deltaY);
                                                    topLeft = newTopLeft;
                                                }
                                                /**
                                                 * Scales the rectangle by a desired factor.
                                                 * @param scaleFactor **factor you want to scale by.
                                                 */
                                                public void scale(double scaleFactor){
                                                    width = (int)Math.round((width * scaleFactor));
                                                    height = (int)Math.round((height * scaleFactor));
                                                }
                                                /**
                                                 * Returns a string with the coordinates, width, and height.
                                                 * @return rectangle **a string that prints information about the rectangle**
                                                 */
                                                public String toString(){
                                                    String rectangle = "[(" + topLeft.x + "," + topLeft.y + ") " + width + "," + height + "]";
                                                    return rectangle;
                                                }
                                                /**
                                                 * Returns true if equal and false if not equal.
                                                 * @param other **another rectangle you want to compare.
                                                 * @return true only if they have same topLeft, width, and height.
                                                 */
                                                public boolean equals(MyRectangle other){
                                                    boolean sameTop = false;
                                                    boolean sameWidth = false;
                                                    boolean sameHeight = false;
                                                    if (this.topLeft.x == other.topLeft.x && this.topLeft.y == other.topLeft.y){
                                                        sameTop = true;
                                                    }
                                                    if (this.width == other.width){
                                                        sameWidth = true;
                                                    }
                                                    if (this.height == other.height){
                                                        sameHeight = true;
                                                    }
                                                    return sameTop == sameWidth == sameHeight;
                                                }
                                                """);
    }};
}
