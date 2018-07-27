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
        // params: lrlon, ullon, w, h, ullat, lrlat
        // System.out.println(params);
        Map<String, Object> results = new HashMap<>();
        double LRLON = params.get("lrlon");
        double ULLON = params.get("ullon");
        double ULLAT = params.get("ullat");
        double LRLAT = params.get("lrlat");
        double width = params.get("w");

        // Calculate the depth of the raster query.
        int depth = depth(lonDPP(LRLON, ULLON, width));

        // Find the x and y values for the top left and bottom right image files
        Map<String, Integer> coords = getborderTiles(depth, ULLON, ULLAT, LRLON, LRLAT);
        int lrX = coords.get("x_right");
        int lrY = coords.get("y_right");
        int ulX = coords.get("x_left");
        int ulY = coords.get("y_left");

        int row = lrX - ulX + 1;
        int col = lrY - ulY + 1;
        String[][] grid = new String[col][row];
        for (int i = ulY; i < col + ulY; i += 1) {
            for (int j = ulX; j < row + ulX; j += 1) {
                grid[i - ulY][j - ulX] = "d" + depth + "_x" + j + "_y" + i + ".png";
            }
        }

        double incrX = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / Math.pow(2, depth);
        double incrY = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / Math.pow(2, depth);

        results.put("render_grid", grid);
        results.put("depth", depth);
        results.put("raster_ul_lon", MapServer.ROOT_ULLON + (ulX * incrX));
        results.put("raster_ul_lat", MapServer.ROOT_ULLAT - (ulY * incrY));
        results.put("raster_lr_lon", MapServer.ROOT_ULLON + ((lrX + 1) * incrX));
        results.put("raster_lr_lat", MapServer.ROOT_ULLAT - ((lrY + 1) * incrY));
        results.put("query_success", true);

        return results;
    }

    private static double lonDPP(double lrlon, double ullon, double width) {
        return (lrlon - ullon) / width;
    }

    private static int depth(double lonDPP) {
        int depth = 0;
        double curr_lonDPP = lonDPP(MapServer.ROOT_LRLON, MapServer.ROOT_ULLON, MapServer.TILE_SIZE);
        while (curr_lonDPP > lonDPP && depth < 7) {
            curr_lonDPP = curr_lonDPP / 2.0;
            depth += 1;
        }
        return depth;
    }

    private static Map<String, Integer> getborderTiles(int depth, double ref_ullon, double ref_ullat,
                                                       double ref_lrlon, double ref_lrlat) {
        Map<String, Integer> res = new HashMap<>();
        double incrX = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / Math.pow(2, depth);
        double incrY = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / Math.pow(2, depth);

        double xl_dist = Math.abs(MapServer.ROOT_ULLON - ref_ullon);
        double yl_dist = Math.abs(MapServer.ROOT_ULLAT - ref_ullat);
        double xr_dist = Math.abs(MapServer.ROOT_LRLON - ref_lrlon);
        double yr_dist = Math.abs(MapServer.ROOT_LRLAT - ref_lrlat);

        int x_left = (int) Math.floor(xl_dist / incrX);
        int y_left = (int) Math.floor(yl_dist / incrY);
        int x_right = (int) Math.pow(2, depth) - (int) Math.floor(xr_dist / incrX) - 1;
        int y_right = (int) Math.pow(2, depth) - (int) Math.floor(yr_dist / incrY) - 1;

        res.put("x_left", x_left);
        res.put("y_left", y_left);
        res.put("x_right", x_right);
        res.put("y_right", y_right);

        return res;
    }

//    public static void main(String[] args) {
//        Rasterer r = new Rasterer();
//        HashMap<String, Double> params_test = new HashMap<>();
//        params_test.put("lrlon", -122.24053369025242);
//        params_test.put("ullon", -122.24163047377972);
//        params_test.put("w", 892.0);
//        params_test.put("h", 875.0);
//        params_test.put("ullat", 37.87655856892288);
//        params_test.put("lrlat", 37.87548268822065);
//        r.getMapRaster(params_test);
//
//        HashMap<String, Double> params_test1234 = new HashMap<>();
//        params_test1234.put("lrlon", -122.20908713544797);
//        params_test1234.put("ullon", -122.3027284165759);
//        params_test1234.put("w", 305.0);
//        params_test1234.put("h", 300.0);
//        params_test1234.put("ullat", 37.88708748276975);
//        params_test1234.put("lrlat", 37.848731523430196);
//        r.getMapRaster(params_test1234);
//
//        HashMap<String, Double> params_twelve = new HashMap<>();
//        params_twelve.put("lrlon", -122.2104604264636);
//        params_twelve.put("ullon", -122.30410170759153);
//        params_twelve.put("w", 1091.0);
//        params_twelve.put("h", 566.0);
//        params_twelve.put("ullat", 37.870213571328854);
//        params_twelve.put("lrlat", 37.8318576119893);
//        r.getMapRaster(params_twelve);
//    }
}