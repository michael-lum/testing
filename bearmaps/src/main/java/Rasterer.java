import java.util.HashMap;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {

    public Rasterer() {
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image;
     *                    can also be interpreted as the length of the numbers in the image
     *                    string. <br>
     * "query_success" : Boolean, whether the query was able to successfully complete; don't
     *                    forget to set this to true on success! <br>
     */
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        Map<String, Object> results = new HashMap<>();

        // Calculate the depth of the raster query.
        int depth = depth(lonDPP(params.get("lrlon"), params.get("ullon"), params.get("w")));
        results.put("depth", depth);

        // Find the x and y values for the top left and bottom right image files
        Map<String, Integer> coords = getborderTiles(depth, params.get("ullon"), params.get("ullat"),
                params.get("lrlon"), params.get("lrlat"));

        int lrX = coords.get("x_right");
        int lrY = coords.get("y_right");
        int ulX = coords.get("x_left");
        int ulY = coords.get("y_left");


        // Create the 2D Array of Strings with file names.
        int row = lrX - ulX + 1;
        int col = lrY - ulY + 1;

        try {
            String[][] grid = new String[col][row];
            for (int i = ulY; i < col + ulY; i += 1) {
                for (int j = ulX; j < row + ulX; j += 1) {
                    grid[i - ulY][j - ulX] = "d" + depth + "_x" + j + "_y" + i + ".png";
                }
            }
            results.put("render_grid", grid);
            results.put("query_success", true);
        } catch (IndexOutOfBoundsException e) {
            results.put("render_grid", null);
            results.put("query_success", false);
        }

        // Calculate the increments for the X direction and Y direction for the raster bounds.
        double incrX = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / Math.pow(2, depth);
        double incrY = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / Math.pow(2, depth);

        // Input values to be returned in results.
        results.put("raster_ul_lon", MapServer.ROOT_ULLON + (ulX * incrX));
        results.put("raster_ul_lat", MapServer.ROOT_ULLAT - (ulY * incrY));
        results.put("raster_lr_lon", MapServer.ROOT_ULLON + ((lrX + 1) * incrX));
        results.put("raster_lr_lat", MapServer.ROOT_ULLAT - ((lrY + 1) * incrY));

        return results;
    }

    /**
     * Finds the lonDPP for the given coordinates and width.
     *
     * @param lrlon Lower right longitude of the provided location.
     * @param ullon Upper left longitude of the provided location.
     * @param width Width of the query box that is specified by the user.
     *
     * @return Longitudinal Distance Per Pixel for calculating the desired resolution.
     */
    private static double lonDPP(double lrlon, double ullon, double width) {
        return (lrlon - ullon) / width;
    }

    /**
     * Calculates the depth of the image.  The depth of the image calculate the greatest lonDPP that is
     * less than or equal to the requested lonDPP, an image can have a maximum depth of 7.
     *
     * @param lonDPP Longitudinal Distance Per Pixel for calculating the desired resolution.
     *
     * @return Depth, int which represents which level of zoom to use.
     */
    private static int depth(double lonDPP) {
        int depth = 0;
        double curr_lonDPP = lonDPP(MapServer.ROOT_LRLON, MapServer.ROOT_ULLON, MapServer.TILE_SIZE);
        while (curr_lonDPP > lonDPP && depth < 7) {
            curr_lonDPP = curr_lonDPP / 2.0;
            depth += 1;
        }
        return depth;
    }

    /**
     * Given a query box, calculates the x and y coordinates for the upper left corner as well as the
     * lower right corner.  These integers represent the start and stop values for the x and y coordinates
     * which will be turned into their respective file names.
     *
     * @param depth Level of zoom.
     * @param ref_ullon User requested upper left longitude.
     * @param ref_ullat User requested upper left latitude.
     * @param ref_lrlon User requested lower right longitude.
     * @param ref_lrlat User requested lower right latitude.
     *
     * @return A map of results for the front end as specified: <br>
     * "x_left"  : Integer, the bounding upper left file on the horizontal plane. <br>
     * "y_left"  : Integer, the bounding upper left file on the vertical plane. <br>
     * "x_right" : Integer, the bounding lower right file on the horizontal plane. <br>
     * "y_right" : Integer, the bounding lower right file on the vertical plane. <br>
     */
    private static Map<String, Integer> getborderTiles(int depth, double ref_ullon, double ref_ullat,
                                                       double ref_lrlon, double ref_lrlat) {
        Map<String, Integer> results = new HashMap<>();
        // Calculate the increments for the X direction and Y direction.
        double incrX = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / Math.pow(2, depth);
        double incrY = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / Math.pow(2, depth);

        // Calculate the distances between the ROOT coordinates and reference coordinates.
        double xL_dist = Math.abs(MapServer.ROOT_ULLON - ref_ullon);
        double yL_dist = Math.abs(MapServer.ROOT_ULLAT - ref_ullat);
        double xR_dist = Math.abs(MapServer.ROOT_LRLON - ref_lrlon);
        double yR_dist = Math.abs(MapServer.ROOT_LRLAT - ref_lrlat);

        // Based on the difference between ROOT and reference, counts how many "tiles" are needed.
        int ulX = (int) Math.floor(xL_dist / incrX);
        int ulY = (int) Math.floor(yL_dist / incrY);
        int lrX = (int) Math.pow(2, depth) - (int) Math.floor(xR_dist / incrX) - 1;
        int lrY = (int) Math.pow(2, depth) - (int) Math.floor(yR_dist / incrY) - 1;

        // Input values to be returned in results.
        results.put("x_left", ulX);
        results.put("y_left", ulY);
        results.put("x_right", lrX);
        results.put("y_right", lrY);

        return results;
    }

    public static void main(String[] args) {
        Rasterer r = new Rasterer();
        HashMap<String, Double> params_test = new HashMap<>();
        params_test.put("lrlon", -122.24053369025242);
        params_test.put("ullon", -122.24163047377972);
        params_test.put("w", 892.0);
        params_test.put("h", 875.0);
        params_test.put("ullat", 37.87655856892288);
        params_test.put("lrlat", 37.87548268822065);
        r.getMapRaster(params_test);

        HashMap<String, Double> params_test1234 = new HashMap<>();
        params_test1234.put("lrlon", -122.20908713544797);
        params_test1234.put("ullon", -122.3027284165759);
        params_test1234.put("w", 305.0);
        params_test1234.put("h", 300.0);
        params_test1234.put("ullat", 37.88708748276975);
        params_test1234.put("lrlat", 37.848731523430196);
        r.getMapRaster(params_test1234);

        HashMap<String, Double> params_twelve = new HashMap<>();
        params_twelve.put("lrlon", -122.2104604264636);
        params_twelve.put("ullon", -122.30410170759153);
        params_twelve.put("w", 1091.0);
        params_twelve.put("h", 566.0);
        params_twelve.put("ullat", 37.870213571328854);
        params_twelve.put("lrlat", 37.8318576119893);
        r.getMapRaster(params_twelve);
    }
}