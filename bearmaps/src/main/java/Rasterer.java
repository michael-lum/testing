import java.util.HashMap;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {
    /** The max image depth level. */
    public static final int MAX_DEPTH = 7;

    /**
     * Takes a user query and finds the grid of images that best matches the query. These images
     * will be combined into one big image (rastered) by the front end. The grid of images must obey
     * the following properties, where image in the grid is referred to as a "tile".
     * <ul>
     *     <li>The tiles collected must cover the most longitudinal distance per pixel (LonDPP)
     *     possible, while still covering less than or equal to the amount of longitudinal distance
     *     per pixel in the query box for the user viewport size.</li>
     *     <li>Contains all tiles that intersect the query bounding box that fulfill the above
     *     condition.</li>
     *     <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     * </ul>
     * @param params The RasterRequestParams containing coordinates of the query box and the browser
     *               viewport width and height.
     * @return A valid RasterResultParams containing the computed results.
     */
    public RasterResultParams getMapRaster(RasterRequestParams params) {
        RasterResultParams.Builder results = new RasterResultParams.Builder();

        // Calculate the depth of the raster query.
        int depth = depth(lonDPP(params.lrlon, params.ullon, params.w));
        results.setDepth(depth);

        // Find the x and y values for the top left and bottom right image files
        Map<String, Integer> coords = getborderTiles(depth, params.ullon, params.ullat,
                params.lrlon, params.lrlat);

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
            results.setRenderGrid(grid);
            results.setQuerySuccess(true);
        } catch (Exception e) {
            return RasterResultParams.queryFailed();
        }

        // Calculate the increments for the X direction and Y direction for the raster bounds.
        double incrX = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / Math.pow(2, depth);
        double incrY = (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / Math.pow(2, depth);

        // Input values to be returned in results.
        results.setRasterUlLon(MapServer.ROOT_ULLON + (ulX * incrX));
        results.setRasterUlLat(MapServer.ROOT_ULLAT - (ulY * incrY));
        results.setRasterLrLon(MapServer.ROOT_ULLON + ((lrX + 1) * incrX));
        results.setRasterLrLat(MapServer.ROOT_ULLAT - ((lrY + 1) * incrY));

        return results.create();


    }

    /**
     * Calculates the lonDPP of an image or query box
     * @param lrlon Lower right longitudinal value of the image or query box
     * @param ullon Upper left longitudinal value of the image or query box
     * @param width Width of the query box or image
     * @return lonDPP
     */
    private double lonDPP(double lrlon, double ullon, double width) {
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
    private int depth(double lonDPP) {
        int depth = 0;
        double curr_lonDPP = lonDPP(MapServer.ROOT_LRLON, MapServer.ROOT_ULLON, MapServer.TILE_SIZE);
        while (curr_lonDPP > lonDPP && depth < MAX_DEPTH) {
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

}
