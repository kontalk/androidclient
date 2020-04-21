Vectors in here needs to be adapted for use with androidsvgdrawable:

1. Remove 9-patch pixels
2. Resize the image to -2 pixels and move content -1 pixels both up and left because of the removed 9-patch parts
3. Setup 9-patch configuration in themes/9patch.json
4. Now vector is ready to be promoted to the themes folder
