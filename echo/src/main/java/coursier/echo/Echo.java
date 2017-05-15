package coursier.echo;

public class Echo {

    public static void main(String[] args) {

        boolean isFirst = true;

        for (String arg : args) {

            if (isFirst)
                isFirst = false;
            else
                System.out.print(" ");

            System.out.print(arg);
        }

        System.out.println();
    }

}
