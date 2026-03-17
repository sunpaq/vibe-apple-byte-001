package com.applebyte.wounddetector.util

import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class SfmProcessor(private val markerSizeMm: Float = 50f) {

    private val orb = ORB.create()
    private val bfMatcher = BFMatcher(DescriptorMatcher.BRUTEFORCE_HAMMING, true)

    private val cameraMatrix = Mat(3, 3, CvType.CV_64FC1).apply {
        put(0, 0, 
            1.0, 0.0, 320.0,
            0.0, 1.0, 240.0,
            0.0, 0.0, 1.0
        )
    }

    private val distCoeffs = Mat.zeros(5, 1, CvType.CV_64FC1)

    data class ImageFeatures(
        val keypoints: MatOfKeyPoint,
        val descriptors: Mat,
        val image: Mat
    )

    fun extractFeatures(images: List<Mat>): List<ImageFeatures> {
        return images.mapNotNull { image ->
            try {
                val gray = Mat()
                Imgproc.cvtColor(image, gray, Imgproc.COLOR_RGBA2GRAY)

                val keypoints = MatOfKeyPoint()
                val descriptors = Mat()

                orb.detectAndCompute(gray, Mat(), keypoints, descriptors)

                if (keypoints.empty() || descriptors.empty()) {
                    null
                } else {
                    ImageFeatures(keypoints, descriptors, image)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun matchFeatures(features1: ImageFeatures, features2: ImageFeatures): List<DMatch> {
        val matches = MatOfDMatch()
        bfMatcher.match(features1.descriptors, features2.descriptors, matches)
        return matches.toList()
            .sortedBy { it.distance }
            .take(50)
    }

    fun estimateCameraPose(
        features1: ImageFeatures,
        features2: ImageFeatures,
        matches: List<DMatch>
    ): Pair<Mat, Mat>? {
        if (matches.size < 10) return null

        val pts1 = mutableListOf<Point>()
        val pts2 = mutableListOf<Point>()

        matches.forEach { match ->
            val kp1 = features1.keypoints.toList()[match.queryIdx]
            val kp2 = features2.keypoints.toList()[match.trainIdx]
            pts1.add(kp1.pt)
            pts2.add(kp2.pt)
        }

        val pts1Mat = MatOfPoint2f(*pts1.toTypedArray())
        val pts2Mat = MatOfPoint2f(*pts2.toTypedArray())

        val essentialMatrix = Calib3d.findEssentialMat(pts1Mat, pts2Mat, cameraMatrix)
            ?: return null

        val rvec = Mat()
        val tvec = Mat()

        val recoverResult = Calib3d.recoverPose(essentialMatrix, pts1Mat, pts2Mat, cameraMatrix, rvec, tvec)

        if (recoverResult <= 0) return null

        return Pair(rvec, tvec)
    }

    fun triangulatePoints(
        features1: ImageFeatures,
        features2: ImageFeatures,
        matches: List<DMatch>,
        rvec1: Mat,
        tvec1: Mat,
        rvec2: Mat,
        tvec2: Mat
    ): MatOfPoint3f? {
        if (matches.size < 5) return null

        val projMat1 = Mat(3, 4, CvType.CV_64FC1)
        val projMat2 = Mat(3, 4, CvType.CV_64FC1)

        val rot1 = Mat()
        Calib3d.Rodrigues(rvec1, rot1)
        val rot2 = Mat()
        Calib3d.Rodrigues(rvec2, rot2)

        rot1.col(0).copyTo(projMat1.col(0))
        rot1.col(1).copyTo(projMat1.col(1))
        rot1.col(2).copyTo(projMat1.col(2))
        tvec1.copyTo(projMat1.col(3))
        Core.gemm(cameraMatrix, projMat1, 1.0, Mat(), 0.0, projMat1)

        rot2.col(0).copyTo(projMat2.col(0))
        rot2.col(1).copyTo(projMat2.col(1))
        rot2.col(2).copyTo(projMat2.col(2))
        tvec2.copyTo(projMat2.col(3))
        Core.gemm(cameraMatrix, projMat2, 1.0, Mat(), 0.0, projMat2)

        val pts1 = mutableListOf<Point>()
        val pts2 = mutableListOf<Point>()

        matches.forEach { match ->
            val kp1 = features1.keypoints.toList()[match.queryIdx]
            val kp2 = features2.keypoints.toList()[match.trainIdx]
            pts1.add(kp1.pt)
            pts2.add(kp2.pt)
        }

        val pts1Mat = MatOfPoint2f(*pts1.toTypedArray())
        val pts2Mat = MatOfPoint2f(*pts2.toTypedArray())

        val points4D = Mat()
        try {
            Calib3d.triangulatePoints(projMat1, projMat2, pts1Mat, pts2Mat, points4D)

            val points3D = MatOfPoint3f()
            for (i in 0 until points4D.cols()) {
                val x = points4D.get(0, i)[0] / points4D.get(3, i)[0]
                val y = points4D.get(1, i)[0] / points4D.get(3, i)[0]
                val z = points4D.get(2, i)[0] / points4D.get(3, i)[0]
                if (z > 0) {
                    points3D.push_back(MatOfPoint3f(Point3(x, y, z)))
                }
            }

            return points3D
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun processImages(images: List<Mat>, arucoDetector: ArUcoDetector): SfMResult {
        if (images.size < 3) {
            return SfMResult(emptyList(), emptyList(), null, false, "Need at least 3 images")
        }

        val features = extractFeatures(images)
        if (features.size < 2) {
            return SfMResult(emptyList(), emptyList(), null, false, "Could not extract features")
        }

        val allPoints3d = mutableListOf<Point3>()
        val cameraPoses = mutableListOf<Mat>()

        var prevFeatures = features[0]
        var prevRvec = Mat.zeros(3, 1, CvType.CV_64FC1)
        var prevTvec = Mat.zeros(3, 1, CvType.CV_64FC1)
        cameraPoses.add(prevRvec.clone())
        cameraPoses.add(prevTvec.clone())

        for (i in 1 until features.size) {
            val currentFeatures = features[i]
            val matches = matchFeatures(prevFeatures, currentFeatures)

            if (matches.size < 10) continue

            val pose = estimateCameraPose(prevFeatures, currentFeatures, matches)
            if (pose == null) continue

            val (rvec, tvec) = pose
            cameraPoses.add(rvec.clone())
            cameraPoses.add(tvec.clone())

            val points3d = triangulatePoints(
                prevFeatures, currentFeatures, matches,
                prevRvec, prevTvec, rvec, tvec
            )

            if (points3d != null) {
                val pointsArray = points3d.toArray()
                allPoints3d.addAll(pointsArray.map { Point3(it.x, it.y, it.z) })
            }

            prevFeatures = currentFeatures
            prevRvec = rvec
            prevTvec = tvec
        }

        if (allPoints3d.isEmpty()) {
            return SfMResult(emptyList(), cameraPoses, null, false, "Could not triangulate points")
        }

        val depthMap = estimateDepthMap(allPoints3d, cameraPoses)

        return SfMResult(
            points3d = allPoints3d,
            cameraPoses = cameraPoses,
            depthMap = depthMap,
            success = true,
            errorMessage = null
        )
    }

    private fun estimateDepthMap(points3d: List<Point3>, cameraPoses: List<Mat>): Mat? {
        if (points3d.isEmpty()) return null

        val depthMap = Mat(points3d.size, 1, CvType.CV_64FC1)

        points3d.forEachIndexed { index, point ->
            depthMap.put(index, 0, point.z)
        }

        return depthMap
    }

    fun estimateDepthAtPoint(point: Point, image: Mat, sfmResult: SfMResult): Double {
        if (sfmResult.points3d.isEmpty()) return 0.0

        val x = point.x
        val y = point.y

        val avgDepth = sfmResult.points3d
            .filter { abs(it.x - x) < 50 && abs(it.y - y) < 50 }
            .map { it.z }
            .average()

        return if (avgDepth.isNaN()) {
            sfmResult.points3d.map { it.z }.average()
        } else {
            avgDepth
        }
    }

    fun calculatePhysicalDistance(zDepth: Double, markerSizePx: Float): Double {
        val scaleFactor = markerSizeMm / markerSizePx
        return zDepth * scaleFactor
    }
}
