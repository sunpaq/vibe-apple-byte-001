package com.applebyte.wounddetector.util

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import kotlin.math.sqrt

data class DepthMapResult(
    val depthMap: Mat,
    val pointCloud: List<Point3>,
    val cameraPoses: List<Mat>,
    val pixelsPerMm: Float
)

class SfMProcessor {
    private val minMatches = 10
    private val featureDetector = ORB.create()
    private val descriptorMatcher = BFMatcher.create()
    
    fun processImages(bitmaps: List<Bitmap>, pixelsPerMm: Float): DepthMapResult? {
        if (bitmaps.size < 2) return null
        
        val images = bitmaps.map { bitmap ->
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
            mat
        }
        
        val keypointsList = mutableListOf<MatOfKeyPoint>()
        val descriptorsList = mutableListOf<Mat>()
        
        for (image in images) {
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            featureDetector.detectAndCompute(image, Mat(), keypoints, descriptors)
            keypointsList.add(keypoints)
            descriptorsList.add(descriptors)
        }
        
        val allMatches = mutableListOf<MatOfDMatch>()
        for (i in 0 until images.size - 1) {
            val matches = MatOfDMatch()
            descriptorMatcher.match(descriptorsList[i], descriptorsList[i + 1], matches)
            val filteredMatches = filterMatches(matches, keypointsList[i], keypointsList[i + 1])
            if (filteredMatches != null && filteredMatches.rows() >= minMatches) {
                allMatches.add(filteredMatches)
            }
        }
        
        if (allMatches.isEmpty()) {
            return null
        }
        
        val poses = estimateCameraPoses(images, keypointsList, allMatches)
        val pointCloud = generatePointCloud(images, keypointsList, allMatches, poses)
        
        val depthMap = computeDepthMap(pointCloud, images[0].cols(), images[0].rows())
        
        return DepthMapResult(
            depthMap = depthMap,
            pointCloud = pointCloud,
            cameraPoses = poses,
            pixelsPerMm = pixelsPerMm
        )
    }
    
    private fun filterMatches(matches: MatOfDMatch, kp1: MatOfKeyPoint, kp2: MatOfKeyPoint): MatOfDMatch? {
        val matchesList = matches.toList()
        val filtered = matchesList.filter { it.distance < 50 }
        if (filtered.size < minMatches) return null
        return MatOfDMatch(*filtered.toTypedArray())
    }
    
    private fun estimateCameraPoses(
        images: List<Mat>,
        keypointsList: List<MatOfKeyPoint>,
        matchesList: List<MatOfDMatch>
    ): List<Mat> {
        val poses = mutableListOf<Mat>()
        
        val k = Mat.eye(3, 3, CvType.CV_64F)
        k.put(0, 0, 500.0)
        k.put(1, 1, 500.0)
        k.put(0, 2, images[0].cols() / 2.0)
        k.put(1, 2, images[0].rows() / 2.0)
        
        var currentR = Mat.eye(3, 3, CvType.CV_64F)
        var currentT = Mat.zeros(3, 1, CvType.CV_64F)
        
        poses.add(currentT.clone())
        
        for (i in matchesList.indices) {
            val pts1 = getPointsFromMatches(matchesList[i], keypointsList[i])
            val pts2 = getPointsFromMatches(matchesList[i], keypointsList[i + 1])
            
            val essentialMatrix = Calib3d.findEssentialMat(pts1, pts2, k)
            
            val r = Mat()
            val t = Mat()
            Calib3d.recoverPose(essentialMatrix, pts1, pts2, k, r, t)
            
            Core.gemm(currentR, r, 1.0, Mat(), 0.0, currentR)
            
            val tTransform = Mat()
            Core.gemm(currentR.t(), t, 1.0, Mat(), 0.0, tTransform)
            Core.add(currentT, tTransform, currentT)
            
            poses.add(currentT.clone())
        }
        
        return poses
    }
    
    private fun getPointsFromMatches(matches: MatOfDMatch, keypoints: MatOfKeyPoint): MatOfPoint2f {
        val kp = keypoints.toList()
        val matchList = matches.toList()
        val points = matchList.map { kp[it.queryIdx].pt }
        return MatOfPoint2f(*points.toTypedArray())
    }
    
    private fun generatePointCloud(
        images: List<Mat>,
        keypointsList: List<MatOfKeyPoint>,
        matchesList: List<MatOfDMatch>,
        poses: List<Mat>
    ): List<Point3> {
        val points = mutableListOf<Point3>()
        
        val k = Mat.eye(3, 3, CvType.CV_64F)
        k.put(0, 0, 500.0)
        k.put(1, 1, 500.0)
        k.put(0, 2, images[0].cols() / 2.0)
        k.put(1, 2, images[0].rows() / 2.0)
        
        for (i in matchesList.indices) {
            val pts1 = getPointsFromMatches(matchesList[i], keypointsList[i])
            val pts2 = getPointsFromMatches(matchesList[i], keypointsList[i + 1])
            
            val triPoints = MatOfPoint3f()
            try {
                val projMatrix1 = Mat(3, 4, CvType.CV_64F)
                val projMatrix2 = Mat(3, 4, CvType.CV_64F)
                
                k.convertTo(projMatrix1, CvType.CV_64F)
                poses[i].t().convertTo(projMatrix2, CvType.CV_64F)
                
                Calib3d.triangulatePoints(projMatrix1, projMatrix2, pts1, pts2, triPoints)
                
                val triList = triPoints.toList()
                for (j in triList.indices step 4) {
                    if (j + 3 < triList.size) {
                        val pt = triList[j]
                        val w = triList[j + 3].x
                        if (w != 0.0) {
                            val x = pt.x / w
                            val y = pt.y / w
                            val z = pt.z / w
                            
                            if (z > 0 && z < 1000) {
                                points.add(Point3(x, y, z))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return points
    }
    
    private fun computeDepthMap(pointCloud: List<Point3>, width: Int, height: Int): Mat {
        val depthMap = Mat.zeros(height, width, CvType.CV_32F)
        
        if (pointCloud.isEmpty()) {
            return depthMap
        }
        
        val minX = pointCloud.map { it.x }.minOrNull() ?: 0.0
        val maxX = pointCloud.map { it.x }.maxOrNull() ?: 1.0
        val minZ = pointCloud.map { it.z }.minOrNull() ?: 0.0
        val maxZ = pointCloud.map { it.z }.maxOrNull() ?: 1.0
        
        for (point in pointCloud) {
            val u = (((point.x - minX) / (maxX - minX)) * (width - 1)).toInt().coerceIn(0, width - 1)
            val v = (((point.y - minX) / (maxX - minX)) * (height - 1)).toInt().coerceIn(0, height - 1)
            
            val normalizedZ = ((point.z - minZ) / (maxZ - minZ) * 255).toFloat()
            depthMap.put(v, u, floatArrayOf(normalizedZ))
        }
        
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.medianBlur(depthMap, depthMap, 5)
        Imgproc.morphologyEx(depthMap, depthMap, Imgproc.MORPH_CLOSE, kernel)
        
        return depthMap
    }
    
    fun getDepthAtPoint(depthMap: Mat, x: Int, y: Int): Float {
        if (x < 0 || y < 0 || x >= depthMap.cols() || y >= depthMap.rows()) {
            return 0f
        }
        val arr = depthMap.get(y, x)
        return if (arr.isNotEmpty()) arr[0].toFloat() else 0f
    }
    
    fun estimateDepthAtRegion(depthMap: Mat, region: Rect): Float {
        var sum = 0f
        var count = 0
        
        val startX = region.x.coerceIn(0, depthMap.cols() - 1)
        val endX = (region.x + region.width).coerceIn(0, depthMap.cols() - 1)
        val startY = region.y.coerceIn(0, depthMap.rows() - 1)
        val endY = (region.y + region.height).coerceIn(0, depthMap.rows() - 1)
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val arr = depthMap.get(y, x)
                if (arr.isNotEmpty()) {
                    val depth = arr[0].toFloat()
                    if (depth > 0) {
                        sum += depth
                        count++
                    }
                }
            }
        }
        
        return if (count > 0) sum / count else 0f
    }
}
