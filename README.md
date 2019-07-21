# aimobile 
# ai for mobile develop
1. NN model: face detect using deep learning
   1.1 training the aimobile ssd NN model for face detect in GPU of PC
   1.2 inference the aimobile ssd NN model for face detect in mobile phone
   1.3 aimobile ssd NN model parmeter is 650K
   1.4 support dark light,shelter from, facial expression,Face Posture(Pitch/Roll/Yaw),different distance(about 1m)
　 1.5 need improve 
       the Face Posture with big Pitch/Roll/Yaw more than 45 degree
       support mutil face
    
2. support Deep learning framework
   2.1 caffe (supported)
   2.2 tensorflow (doing)
   2.3 pytorch (doing)

3. Inference performance
   now is 11fps in mobile phone with A73 arm cpu
   our target is 30fps in mobile phone with arm cpu   

4. apk demo setup 
   4.1 put the aimobile ssd model and pt into /sdcard/aimobile   
   4.2 install the apk
   4.3 allow the camera permission

Can not be used for commercial purposes without permission

create aimobile by 2019/07/21:00:19
