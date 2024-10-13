This is a small helper service that will assist you in creating a Teamcenter dataset and uploading a file to it. 

The service will return the UID of the created dataset, which you can then use, for example, to link the created dataset to an ItemRevision.

This code has been tested with **Teamcenter version 13.3**, and knowing how carefully Siemens maintains API support, I am confident that this code will work with newer versions of Teamcenter as well.

Please note that you must have access to the Teamcenter distribution to obtain a number of libraries. Navigate to the folder: **src/main/resources/lib**

To build/run the service, you should use: **Oracle JDK 1.8.0_202** 

To configure the service, go to the file: **src/main/resources/application.properties** 

Currently, the **create-dataset** method is implemented for creating a dataset and uploading a file to it.

UPD.On October 13, 2024, I added a method **download-dataset** to download a file from the specified dataset

To quickly test the service, use the Postman collection: **Teamcenter-File-PushPuller.postman_collection.json**

![изображение](https://github.com/user-attachments/assets/df2123c5-f18c-4295-becf-740fb6ac3d48)

![изображение](https://github.com/user-attachments/assets/2b787099-137f-4c82-a8b5-0e8d292d76e9)
